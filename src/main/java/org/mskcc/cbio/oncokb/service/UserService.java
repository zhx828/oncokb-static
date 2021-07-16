package org.mskcc.cbio.oncokb.service;

import com.google.gson.Gson;
import io.github.jhipster.config.JHipsterProperties;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.config.Constants;
import org.mskcc.cbio.oncokb.config.cache.CacheNameResolver;
import org.mskcc.cbio.oncokb.domain.*;
import org.mskcc.cbio.oncokb.domain.enumeration.LicenseType;
import org.mskcc.cbio.oncokb.repository.AuthorityRepository;
import org.mskcc.cbio.oncokb.repository.UserDetailsRepository;
import org.mskcc.cbio.oncokb.repository.UserRepository;
import org.mskcc.cbio.oncokb.security.AuthoritiesConstants;
import org.mskcc.cbio.oncokb.security.SecurityUtils;
import org.mskcc.cbio.oncokb.security.uuid.TokenProvider;
import org.mskcc.cbio.oncokb.service.dto.useradditionalinfo.*;
import org.mskcc.cbio.oncokb.service.dto.UserDTO;
import org.mskcc.cbio.oncokb.service.mapper.UserMapper;

import io.github.jhipster.security.RandomUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.config.Constants.*;
import static org.mskcc.cbio.oncokb.config.cache.UserCacheResolver.USERS_BY_EMAIL_CACHE;
import static org.mskcc.cbio.oncokb.config.cache.UserCacheResolver.USERS_BY_LOGIN_CACHE;

/**
 * Service class for managing users.
 */
@Service
@Transactional
public class UserService {

    private final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final UserDetailsRepository userDetailsRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthorityRepository authorityRepository;

    private final CacheManager cacheManager;

    private final CacheNameResolver cacheNameResolver;

    private final JHipsterProperties jHipsterProperties;

    private final TokenService tokenService;

    private final TokenProvider tokenProvider;

    private final SlackService slackService;

    @Autowired
    private UserMapper userMapper;

    public UserService(
        UserRepository userRepository,
        UserDetailsRepository userDetailsRepository,
        PasswordEncoder passwordEncoder,
        AuthorityRepository authorityRepository,
        JHipsterProperties jHipsterProperties,
        TokenService tokenService,
        TokenProvider tokenProvider,
        CacheNameResolver cacheNameResolver,
        SlackService slackService,
        CacheManager cacheManager
    ) {
        this.userRepository = userRepository;
        this.userDetailsRepository = userDetailsRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorityRepository = authorityRepository;
        this.jHipsterProperties = jHipsterProperties;
        this.tokenService = tokenService;
        this.tokenProvider = tokenProvider;
        this.cacheNameResolver = cacheNameResolver;
        this.cacheManager = cacheManager;
        this.slackService = slackService;
    }

    public Optional<User> activateRegistration(String key) {
        log.debug("Activating user for activation key {}", key);
        return getUserByActivationKey(key)
            .map(user -> {
                // we only set the activation key to null after verifying the email.
                // the account needs to be manually verified by the admin
                user.setActivationKey(null);
                this.clearUserCaches(user);
                log.debug("Activated user: {}", user);
                return user;
            });
    }

    public void generateNewActivationKey(User user) {
        user.setActivationKey(RandomUtil.generateActivationKey());
        userRepository.save(user);
        this.clearUserCaches(user);
    }

    public Optional<User> getUserByActivationKey(String key) {
        return userRepository.findOneByActivationKey(key);
    }

    public Optional<User> completePasswordReset(String newPassword, String key) {
        log.debug("Reset user password for reset key {}", key);
        return userRepository.findOneByResetKey(key)
            .map(user -> {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setResetKey(null);
                user.setResetDate(null);
                this.clearUserCaches(user);
                return user;
            });
    }

    public Optional<User> requestPasswordReset(String mail) {
        return userRepository.findOneByEmailIgnoreCase(mail)
            .filter(User::getActivated)
            .map(user -> {
                user.setResetKey(RandomUtil.generateResetKey());
                user.setResetDate(Instant.now());
                this.clearUserCaches(user);
                return user;
            });
    }

    public Optional<User> initiateTrialAccountActivation(String login) {
        Optional<User> userOptional = userRepository.findOneByLogin(login);
        if (userOptional.isPresent()) {
            Optional<UserDetails> userDetails = userDetailsRepository.findOneByUser(userOptional.get());
            UserDetails ud = null;
            if (userDetails.isPresent()) {
                ud = userDetails.get();
            } else {
                ud = new UserDetails();
                ud.setUser(userOptional.get());
            }

            AdditionalInfoDTO additionalInfoDTO = null;
            if (userDetails.isPresent()) {
                additionalInfoDTO = new Gson().fromJson(ud.getAdditionalInfo(), AdditionalInfoDTO.class);
            }
            if (additionalInfoDTO == null) {
                additionalInfoDTO = new AdditionalInfoDTO();
            }
            additionalInfoDTO.setTrialAccount(initiateTrialAccountInfo());
            ud.setAdditionalInfo(new Gson().toJson(additionalInfoDTO));

            userDetailsRepository.save(ud);
            return userOptional;
        } else {
            return Optional.empty();
        }
    }

    public Optional<UserDTO> finishTrialAccountActivation(String key) {
        Optional<UserDetails> userDetailsOptional = userDetailsRepository.findOneByTrialActivationKey(key);
        User user = userDetailsOptional.get().getUser();
        UserDTO userDTO = userMapper.userToUserDTO(userDetailsOptional.get().getUser());
        boolean userHasValidTrialActivation = Optional.ofNullable(userDTO)
            .map(UserDTO::getAdditionalInfo)
            .map(AdditionalInfoDTO::getTrialAccount)
            .map(TrialAccount::getActivation)
            .isPresent();

        if (userHasValidTrialActivation) {
            String userKey = userDTO.getAdditionalInfo().getTrialAccount().getActivation().getKey();
            if (StringUtils.isNotEmpty(userKey) && userKey.equals(key)) {
                // Update user account to trial account
                user.setActivated(true);
                generateTokenForUserIfNotExistOrOverwrite(userDTO, Optional.of(TRIAL_PERIOD_IN_DAYS), Optional.of(false));

                // Reset the trial account info
                Optional<UserDetails> userDetails = userDetailsRepository.findOneByUser(user);
                TrialAccount trialAccount = userDTO.getAdditionalInfo().getTrialAccount();
                trialAccount.getActivation().setActivationDate(Instant.now());
                trialAccount.getActivation().setKey(null);
                trialAccount.getLicenseAgreement().setAcceptanceDate(Instant.now());
                userDTO.getAdditionalInfo().setTrialAccount(trialAccount);

                userDetails.get().setAdditionalInfo(new Gson().toJson(userDTO.getAdditionalInfo()));
                userDetailsRepository.save(userDetails.get());

                slackService.sendConfirmationOnUserAcceptsTrialAgreement(userDTO, Instant.now().plusSeconds(TRIAL_PERIOD_IN_DAYS * DAY_IN_SECONDS));
                return Optional.of(userDTO);
            }
        }
        return Optional.empty();
    }

    private TrialAccount initiateTrialAccountInfo() {
        TrialAccount trialAccount = new TrialAccount();
        Activation activation = new Activation();
        activation.setInitiationDate(Instant.now());
        activation.setKey(RandomUtil.generateResetKey());
        trialAccount.setActivation(activation);

        LicenseAgreement licenseAgreement = new LicenseAgreement();
        licenseAgreement.setName("Trial License Agreement");
        licenseAgreement.setVersion("v1");
        trialAccount.setLicenseAgreement(licenseAgreement);
        return trialAccount;
    }

    public User registerUser(UserDTO userDTO, String password) {
        userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).ifPresent(existingUser -> {
            throw new EmailAlreadyUsedException();
        });
        User newUser = new User();
        String encryptedPassword = passwordEncoder.encode(password);
        newUser.setLogin(userDTO.getLogin().toLowerCase());
        // new user gets initially a generated password
        newUser.setPassword(encryptedPassword);
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        if (userDTO.getEmail() != null) {
            newUser.setEmail(userDTO.getEmail().toLowerCase());
        }
        newUser.setImageUrl(userDTO.getImageUrl());
        if (userDTO.getLangKey() == null) {
            newUser.setLangKey(Constants.DEFAULT_LANGUAGE); // default language
        } else {
            newUser.setLangKey(userDTO.getLangKey());
        }
        // new user is not active
        newUser.setActivated(false);
        // new user gets registration key
        newUser.setActivationKey(RandomUtil.generateActivationKey());
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);
        userRepository.save(newUser);

        UserDetails userDetails = new UserDetails();
        userDetails.setUser(newUser);
        userDetails.setLicenseType(userDTO.getLicenseType());
        userDetails.setJobTitle(userDTO.getJobTitle());
        userDetails.setCompany(userDTO.getCompany());
        userDetails.setCity(userDTO.getCity());
        userDetails.setCountry(userDTO.getCountry());
        if (userDTO.getAdditionalInfo() != null) {
            userDetails.setAdditionalInfo(new Gson().toJson(userDTO.getAdditionalInfo()));
        }
        userDetailsRepository.save(userDetails);

        this.clearUserCaches(newUser);
        log.debug("Created Information for User: {}", newUser);
        return newUser;
    }

    public boolean trialAccountActivated(UserDTO userDTO) {
        List<Token> tokens = tokenService.findByUser(userMapper.userDTOToUser(userDTO));
        return tokens.stream().filter(token -> !token.isRenewable()).findAny().isPresent();
    }
    public boolean trialAccountInitiated(UserDTO userDTO) {
        if (
            userDTO.getAdditionalInfo() == null
                || userDTO.getAdditionalInfo().getTrialAccount() == null
                || userDTO.getAdditionalInfo().getTrialAccount().getActivation() == null
        ) {
            return false;
        }
        return StringUtils.isNotEmpty(userDTO.getAdditionalInfo().getTrialAccount().getActivation().getKey()) || userDTO.getAdditionalInfo().getTrialAccount().getActivation().getActivationDate() != null;
    }

    public User createUser(UserDTO userDTO, Optional<Integer> tokenValidDays, Optional<Boolean> tokenIsRenewable) {
        User user = new User();
        user.setLogin(userDTO.getLogin().toLowerCase());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail().toLowerCase());
        }
        user.setImageUrl(userDTO.getImageUrl());
        if (userDTO.getLangKey() == null) {
            user.setLangKey(Constants.DEFAULT_LANGUAGE); // default language
        } else {
            user.setLangKey(userDTO.getLangKey());
        }
        String encryptedPassword = passwordEncoder.encode(RandomUtil.generatePassword());
        user.setPassword(encryptedPassword);
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(Instant.now());
        user.setActivated(true);
        if (userDTO.getAuthorities() != null) {
            Set<Authority> authorities = userDTO.getAuthorities().stream()
                .map(authorityRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
            user.setAuthorities(authorities);
        }
        User updatedUser = userRepository.save(user);

        UserDetails userDetails = new UserDetails();
        userDetails.setUser(user);
        userDetails.setLicenseType(userDTO.getLicenseType());
        userDetails.setJobTitle(userDTO.getJobTitle());
        userDetails.setCompany(userDTO.getCompany());
        userDetails.setCity(userDTO.getCity());
        userDetails.setCountry(userDTO.getCountry());
        userDetailsRepository.save(userDetails);

        this.clearUserCaches(user);

        generateTokenForUserIfNotExist(userMapper.userToUserDTO(updatedUser), tokenValidDays, tokenIsRenewable);
        log.debug("Created Information for User: {}", user);
        return user;
    }

    /**
     * Update all information for a specific user, and return the modified user.
     *
     * @param userDTO user to update.
     * @return updated user.
     */
    public Optional<UserDTO> updateUser(UserDTO userDTO) {
        Optional<UserDTO> updatedUserDTO = Optional.of(userRepository
            .findById(userDTO.getId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(user -> {
                this.clearUserCaches(user);
                user.setLogin(userDTO.getLogin().toLowerCase());
                user.setFirstName(userDTO.getFirstName());
                user.setLastName(userDTO.getLastName());
                if (userDTO.getEmail() != null) {
                    user.setEmail(userDTO.getEmail().toLowerCase());
                }
                user.setImageUrl(userDTO.getImageUrl());
                user.setActivated(userDTO.isActivated());
                user.setLangKey(userDTO.getLangKey());
                Set<Authority> managedAuthorities = user.getAuthorities();
                managedAuthorities.clear();
                userDTO.getAuthorities().stream()
                    .map(authorityRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(managedAuthorities::add);
                this.clearUserCaches(user);
                log.debug("Changed Information for User: {}", user);

                return new UserDTO(user, getUpdatedUserDetails(
                    user, userDTO.getLicenseType(), userDTO.getJobTitle(), userDTO.getCompany(), userDTO.getCity(), userDTO.getCountry()));
            });

        if (updatedUserDTO.isPresent()) {
            generateTokenForUserIfNotExist(updatedUserDTO.get(), Optional.empty(), Optional.empty());
        }
        return updatedUserDTO;
    }

    private UserDetails getUpdatedUserDetails(User user, LicenseType licenseType, String jobTitle, String company, String city, String country) {
        Optional<UserDetails> userDetails = userDetailsRepository.findOneByUser(user);
        if (userDetails.isPresent()) {
            userDetails.get().setLicenseType(licenseType);
            userDetails.get().setJobTitle(jobTitle);
            userDetails.get().setCompany(company);
            userDetails.get().setCity(city);
            userDetails.get().setCountry(country);
            return userDetails.get();
        } else {
            UserDetails newUserDetails = new UserDetails();
            newUserDetails.setLicenseType(licenseType);
            newUserDetails.setJobTitle(jobTitle);
            newUserDetails.setCompany(company);
            newUserDetails.setCity(city);
            newUserDetails.setCountry(country);
            newUserDetails.setUser(user);
            userDetailsRepository.save(newUserDetails);
            return newUserDetails;
        }
    }

    public void deleteUser(String login) {
        userRepository.findOneByLogin(login).ifPresent(user -> {
            userRepository.delete(user);
            this.clearUserCaches(user);
            log.debug("Deleted User: {}", user);
        });
    }

    @Transactional
    public void changePassword(String currentClearTextPassword, String newPassword) {
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .ifPresent(user -> {
                String currentEncryptedPassword = user.getPassword();
                if (!passwordEncoder.matches(currentClearTextPassword, currentEncryptedPassword)) {
                    throw new InvalidPasswordException();
                }
                String encryptedPassword = passwordEncoder.encode(newPassword);
                user.setPassword(encryptedPassword);
                this.clearUserCaches(user);
                log.debug("Changed password for User: {}", user);
            });
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAllManagedUsers(Pageable pageable) {
        return userRepository.findAllByLoginNot(pageable, Constants.ANONYMOUS_USER).map(user -> userMapper.userToUserDTO(user));
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthoritiesByLogin(String login) {
        return userRepository.findOneWithAuthoritiesByLogin(login);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthorities() {
        return SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneWithAuthoritiesByLogin);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthoritiesByEmailIgnoreCase(String email) {
        return userRepository.findOneWithAuthoritiesByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAllRegisteredUsers(Pageable pageable) {
        return userRepository.findAllByActivatedIsTrueOrderByCreatedBy(pageable).map(user -> userMapper.userToUserDTO(user));
    }

    public Optional<UserDTO> approveUser(UserDTO userDTO) {
        if (!userDTO.isActivated()) {
            userDTO.setActivated(true);
        }
        Optional<UserDTO> updatedUserDTO = updateUser(userDTO);
        if (updatedUserDTO.isPresent()) {
            generateTokenForUserIfNotExist(updatedUserDTO.get(), Optional.empty(), Optional.empty());
        }
        return updatedUserDTO;
    }

    public void convertTrialUserToRegular(UserDTO userDTO) {
        if (!userDTO.isActivated()) {
            userDTO.setActivated(true);
            Optional<UserDTO> updatedUserDTO = updateUser(userDTO);
            userDTO = updatedUserDTO.get();
        }
        List<Token> tokens = tokenService.findByUser(userMapper.userDTOToUser(userDTO));
        tokens.forEach(token -> {
            token.setRenewable(true);
            token.setExpiration(Instant.now().plusSeconds(HALF_YEAR_IN_SECONDS));
            tokenService.save(token);
        });
    }

    private void generateTokenForUserIfNotExist(UserDTO userDTO, Optional<Integer> tokenValidDays, Optional<Boolean> tokenIsRenewable) {
        // automatically generate a token for user if not exists
        List<Token> tokens = tokenService.findByUser(userMapper.userDTOToUser(userDTO));
        if (tokens.isEmpty()) {
            tokenProvider.createToken(
                userMapper.userDTOToUser(userDTO),
                tokenValidDays.isPresent() ? Optional.of(Instant.now().plusSeconds(DAY_IN_SECONDS * (long) tokenValidDays.get())) : Optional.empty(),
                tokenIsRenewable
            );
        }
    }

    private void generateTokenForUserIfNotExistOrOverwrite(UserDTO userDTO, Optional<Integer> tokenValidDays, Optional<Boolean> tokenIsRenewable) {
        // automatically generate a token for user if not exists
        List<Token> tokens = tokenService.findByUser(userMapper.userDTOToUser(userDTO));
        if (tokens.isEmpty()) {
            tokenProvider.createToken(
                userMapper.userDTOToUser(userDTO),
                tokenValidDays.isPresent() ? Optional.of(Instant.now().plusSeconds(DAY_IN_SECONDS * (long) tokenValidDays.get())) : Optional.empty(),
                tokenIsRenewable
            );
        } else {
            tokens.forEach(token -> {
                token.setRenewable(false);
                token.setExpiration(Instant.now().plusSeconds(DAY_IN_SECONDS * tokenValidDays.get()));
                tokenService.save(token);
            });
        }
    }

    public List<UserDTO> getAllActivatedUsersWithoutTokens() {
        return userRepository.findAllActivatedWithoutTokens().stream().map(user -> userMapper.userToUserDTO(user)).collect(Collectors.toList());
    }

    public List<UserMessagePair> getAllUnapprovedUsersCreatedAfter(int daysAgo) {
        List<UserIdMessagePair> idList = slackService.getAllUnapprovedUserRequestsSentAfter(daysAgo);
        List<UserMessagePair> userList = new ArrayList<>();
        for (UserIdMessagePair userIdMessagePair : idList) {
            userList.add(new UserMessagePair(userMapper.userToUserDTO(userRepository.findOneById(userIdMessagePair.getId()).get()), userIdMessagePair.getMessage()));
        }
        return userList;
    }

    public void removeNotActivatedUsers() {
        userRepository
            .findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant.now().minus(jHipsterProperties.getAuditEvents().getRetentionPeriod(), ChronoUnit.DAYS))
            .forEach(user -> {
                log.debug("Deleting not activated user {}", user.getLogin());
                userRepository.delete(user);
                this.clearUserCaches(user);
            });
    }

    public boolean userHasAuthority(User user, String authority) {
        return user.getAuthorities().stream().filter(userAuth -> userAuth.getName().equalsIgnoreCase(authority)).count() > 0;
    }

    /**
     * Gets a list of all the authorities.
     *
     * @return a list of all the authorities.
     */
    @Transactional(readOnly = true)
    public List<String> getAuthorities() {
        return authorityRepository.findAll().stream().map(Authority::getName).collect(Collectors.toList());
    }


    private void clearUserCaches(User user) {
        Objects.requireNonNull(cacheManager.getCache(this.cacheNameResolver.getCacheName(USERS_BY_LOGIN_CACHE))).evict(user.getLogin());
        if (user.getEmail() != null) {
            Objects.requireNonNull(cacheManager.getCache(this.cacheNameResolver.getCacheName(USERS_BY_EMAIL_CACHE))).evict(user.getEmail());
        }
    }
}
