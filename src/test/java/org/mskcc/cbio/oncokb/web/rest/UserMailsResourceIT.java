package org.mskcc.cbio.oncokb.web.rest;

import org.mskcc.cbio.oncokb.RedisTestContainerExtension;
import org.mskcc.cbio.oncokb.OncokbPublicApp;
import org.mskcc.cbio.oncokb.domain.UserMails;
import org.mskcc.cbio.oncokb.repository.UserMailsRepository;
import org.mskcc.cbio.oncokb.service.UserMailsService;
import org.mskcc.cbio.oncokb.service.dto.UserMailsDTO;
import org.mskcc.cbio.oncokb.service.mapper.UserMailsMapper;
import org.mskcc.cbio.oncokb.web.rest.errors.ExceptionTranslator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mskcc.cbio.oncokb.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.mskcc.cbio.oncokb.domain.enumeration.MailType;
/**
 * Integration tests for the {@link UserMailsResource} REST controller.
 */
@SpringBootTest(classes = OncokbPublicApp.class)
@ExtendWith(RedisTestContainerExtension.class)
public class UserMailsResourceIT {

    private static final Instant DEFAULT_SENT_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_SENT_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_SENT_BY = "AAAAAAAAAA";
    private static final String UPDATED_SENT_BY = "BBBBBBBBBB";

    private static final MailType DEFAULT_MAIL_TYPE = MailType.ACTIVATION;
    private static final MailType UPDATED_MAIL_TYPE = MailType.APPROVAL;

    private static final String DEFAULT_SENT_FROM = "AAAAAAAAAA";
    private static final String UPDATED_SENT_FROM = "BBBBBBBBBB";

    @Autowired
    private UserMailsRepository userMailsRepository;

    @Autowired
    private UserMailsMapper userMailsMapper;

    @Autowired
    private UserMailsService userMailsService;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    @Autowired
    private Validator validator;

    private MockMvc restUserMailsMockMvc;

    private UserMails userMails;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final UserMailsResource userMailsResource = new UserMailsResource(userMailsService);
        this.restUserMailsMockMvc = MockMvcBuilders.standaloneSetup(userMailsResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setConversionService(createFormattingConversionService())
            .setMessageConverters(jacksonMessageConverter)
            .setValidator(validator).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserMails createEntity(EntityManager em) {
        UserMails userMails = new UserMails()
            .sentDate(DEFAULT_SENT_DATE)
            .sentBy(DEFAULT_SENT_BY)
            .mailType(DEFAULT_MAIL_TYPE)
            .sentFrom(DEFAULT_SENT_FROM);
        return userMails;
    }
    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserMails createUpdatedEntity(EntityManager em) {
        UserMails userMails = new UserMails()
            .sentDate(UPDATED_SENT_DATE)
            .sentBy(UPDATED_SENT_BY)
            .mailType(UPDATED_MAIL_TYPE)
            .sentFrom(UPDATED_SENT_FROM);
        return userMails;
    }

    @BeforeEach
    public void initTest() {
        userMails = createEntity(em);
    }

    @Test
    @Transactional
    public void createUserMails() throws Exception {
        int databaseSizeBeforeCreate = userMailsRepository.findAll().size();

        // Create the UserMails
        UserMailsDTO userMailsDTO = userMailsMapper.toDto(userMails);
        restUserMailsMockMvc.perform(post("/api/user-mails")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(userMailsDTO)))
            .andExpect(status().isCreated());

        // Validate the UserMails in the database
        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeCreate + 1);
        UserMails testUserMails = userMailsList.get(userMailsList.size() - 1);
        assertThat(testUserMails.getSentDate()).isEqualTo(DEFAULT_SENT_DATE);
        assertThat(testUserMails.getSentBy()).isEqualTo(DEFAULT_SENT_BY);
        assertThat(testUserMails.getMailType()).isEqualTo(DEFAULT_MAIL_TYPE);
        assertThat(testUserMails.getSentFrom()).isEqualTo(DEFAULT_SENT_FROM);
    }

    @Test
    @Transactional
    public void createUserMailsWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = userMailsRepository.findAll().size();

        // Create the UserMails with an existing ID
        userMails.setId(1L);
        UserMailsDTO userMailsDTO = userMailsMapper.toDto(userMails);

        // An entity with an existing ID cannot be created, so this API call must fail
        restUserMailsMockMvc.perform(post("/api/user-mails")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(userMailsDTO)))
            .andExpect(status().isBadRequest());

        // Validate the UserMails in the database
        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeCreate);
    }


    @Test
    @Transactional
    public void checkSentDateIsRequired() throws Exception {
        int databaseSizeBeforeTest = userMailsRepository.findAll().size();
        // set the field null
        userMails.setSentDate(null);

        // Create the UserMails, which fails.
        UserMailsDTO userMailsDTO = userMailsMapper.toDto(userMails);

        restUserMailsMockMvc.perform(post("/api/user-mails")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(userMailsDTO)))
            .andExpect(status().isBadRequest());

        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void checkSentByIsRequired() throws Exception {
        int databaseSizeBeforeTest = userMailsRepository.findAll().size();
        // set the field null
        userMails.setSentBy(null);

        // Create the UserMails, which fails.
        UserMailsDTO userMailsDTO = userMailsMapper.toDto(userMails);

        restUserMailsMockMvc.perform(post("/api/user-mails")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(userMailsDTO)))
            .andExpect(status().isBadRequest());

        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void checkSentFromIsRequired() throws Exception {
        int databaseSizeBeforeTest = userMailsRepository.findAll().size();
        // set the field null
        userMails.setSentFrom(null);

        // Create the UserMails, which fails.
        UserMailsDTO userMailsDTO = userMailsMapper.toDto(userMails);

        restUserMailsMockMvc.perform(post("/api/user-mails")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(userMailsDTO)))
            .andExpect(status().isBadRequest());

        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void getAllUserMails() throws Exception {
        // Initialize the database
        userMailsRepository.saveAndFlush(userMails);

        // Get all the userMailsList
        restUserMailsMockMvc.perform(get("/api/user-mails?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(userMails.getId().intValue())))
            .andExpect(jsonPath("$.[*].sentDate").value(hasItem(DEFAULT_SENT_DATE.toString())))
            .andExpect(jsonPath("$.[*].sentBy").value(hasItem(DEFAULT_SENT_BY)))
            .andExpect(jsonPath("$.[*].mailType").value(hasItem(DEFAULT_MAIL_TYPE.toString())))
            .andExpect(jsonPath("$.[*].sentFrom").value(hasItem(DEFAULT_SENT_FROM)));
    }
    
    @Test
    @Transactional
    public void getUserMails() throws Exception {
        // Initialize the database
        userMailsRepository.saveAndFlush(userMails);

        // Get the userMails
        restUserMailsMockMvc.perform(get("/api/user-mails/{id}", userMails.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(userMails.getId().intValue()))
            .andExpect(jsonPath("$.sentDate").value(DEFAULT_SENT_DATE.toString()))
            .andExpect(jsonPath("$.sentBy").value(DEFAULT_SENT_BY))
            .andExpect(jsonPath("$.mailType").value(DEFAULT_MAIL_TYPE.toString()))
            .andExpect(jsonPath("$.sentFrom").value(DEFAULT_SENT_FROM));
    }

    @Test
    @Transactional
    public void getNonExistingUserMails() throws Exception {
        // Get the userMails
        restUserMailsMockMvc.perform(get("/api/user-mails/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateUserMails() throws Exception {
        // Initialize the database
        userMailsRepository.saveAndFlush(userMails);

        int databaseSizeBeforeUpdate = userMailsRepository.findAll().size();

        // Update the userMails
        UserMails updatedUserMails = userMailsRepository.findById(userMails.getId()).get();
        // Disconnect from session so that the updates on updatedUserMails are not directly saved in db
        em.detach(updatedUserMails);
        updatedUserMails
            .sentDate(UPDATED_SENT_DATE)
            .sentBy(UPDATED_SENT_BY)
            .mailType(UPDATED_MAIL_TYPE)
            .sentFrom(UPDATED_SENT_FROM);
        UserMailsDTO userMailsDTO = userMailsMapper.toDto(updatedUserMails);

        restUserMailsMockMvc.perform(put("/api/user-mails")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(userMailsDTO)))
            .andExpect(status().isOk());

        // Validate the UserMails in the database
        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeUpdate);
        UserMails testUserMails = userMailsList.get(userMailsList.size() - 1);
        assertThat(testUserMails.getSentDate()).isEqualTo(UPDATED_SENT_DATE);
        assertThat(testUserMails.getSentBy()).isEqualTo(UPDATED_SENT_BY);
        assertThat(testUserMails.getMailType()).isEqualTo(UPDATED_MAIL_TYPE);
        assertThat(testUserMails.getSentFrom()).isEqualTo(UPDATED_SENT_FROM);
    }

    @Test
    @Transactional
    public void updateNonExistingUserMails() throws Exception {
        int databaseSizeBeforeUpdate = userMailsRepository.findAll().size();

        // Create the UserMails
        UserMailsDTO userMailsDTO = userMailsMapper.toDto(userMails);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUserMailsMockMvc.perform(put("/api/user-mails")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(userMailsDTO)))
            .andExpect(status().isBadRequest());

        // Validate the UserMails in the database
        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteUserMails() throws Exception {
        // Initialize the database
        userMailsRepository.saveAndFlush(userMails);

        int databaseSizeBeforeDelete = userMailsRepository.findAll().size();

        // Delete the userMails
        restUserMailsMockMvc.perform(delete("/api/user-mails/{id}", userMails.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<UserMails> userMailsList = userMailsRepository.findAll();
        assertThat(userMailsList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
