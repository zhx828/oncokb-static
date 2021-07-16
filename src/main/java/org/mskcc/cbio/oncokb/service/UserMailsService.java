package org.mskcc.cbio.oncokb.service;

import org.mskcc.cbio.oncokb.domain.User;
import org.mskcc.cbio.oncokb.domain.UserMails;
import org.mskcc.cbio.oncokb.domain.enumeration.MailType;
import org.mskcc.cbio.oncokb.repository.UserMailsRepository;
import org.mskcc.cbio.oncokb.service.dto.UserMailsDTO;
import org.mskcc.cbio.oncokb.service.mapper.UserMailsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing {@link UserMails}.
 */
@Service
@Transactional
public class UserMailsService {

    private final Logger log = LoggerFactory.getLogger(UserMailsService.class);

    private final UserMailsRepository userMailsRepository;

    private final UserMailsMapper userMailsMapper;

    public UserMailsService(UserMailsRepository userMailsRepository, UserMailsMapper userMailsMapper) {
        this.userMailsRepository = userMailsRepository;
        this.userMailsMapper = userMailsMapper;
    }

    /**
     * Save a userMails.
     *
     * @param userMailsDTO the entity to save.
     * @return the persisted entity.
     */
    public UserMailsDTO save(UserMailsDTO userMailsDTO) {
        log.debug("Request to save UserMails : {}", userMailsDTO);
        UserMails userMails = userMailsMapper.toEntity(userMailsDTO);
        userMails = userMailsRepository.save(userMails);
        return userMailsMapper.toDto(userMails);
    }

    /**
     * Get all the userMails.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<UserMailsDTO> findAll() {
        log.debug("Request to get all UserMails");
        return userMailsRepository.findAll().stream()
            .map(userMailsMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }


    /**
     * Get all userMails for a particular user
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<UserMailsDTO> findUserAll(String login) {
        log.debug("Request to get all UserMails for a particular user");
        return userMailsRepository.findByUser(login).stream()
            .map(userMailsMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    @Transactional(readOnly = true)
    public List<UserMailsDTO> findUserMailsByUserAndMailTypeAndSentDateAfter(User user, MailType mailType, Instant sentAfter) {
        log.debug("Request to get all UserMails for a particular user");
        if (sentAfter == null) {
            sentAfter = Instant.EPOCH;
        }

        return userMailsRepository.findUserMailsByUserAndMailTypeAndSentDateAfter(user, mailType, sentAfter).stream()
            .map(userMailsMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one userMails by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<UserMailsDTO> findOne(Long id) {
        log.debug("Request to get UserMails : {}", id);
        return userMailsRepository.findById(id)
            .map(userMailsMapper::toDto);
    }

    /**
     * Delete the userMails by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete UserMails : {}", id);
        userMailsRepository.deleteById(id);
    }
}
