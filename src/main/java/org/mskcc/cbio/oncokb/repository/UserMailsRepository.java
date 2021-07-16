package org.mskcc.cbio.oncokb.repository;
import org.mskcc.cbio.oncokb.domain.User;
import org.mskcc.cbio.oncokb.domain.UserMails;

import org.mskcc.cbio.oncokb.domain.enumeration.MailType;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for the UserMails entity.
 */
@SuppressWarnings("unused")
@Repository
public interface UserMailsRepository extends JpaRepository<UserMails, Long> {

    @Query("select userMails from UserMails userMails where userMails.user.login = ?#{principal.username}")
    List<UserMails> findByUserIsCurrentUser();

    @Query("select userMails from UserMails userMails where userMails.user.login = ?1")
    List<UserMails> findByUser(String login);

    List<UserMails> findUserMailsByUserAndMailTypeAndSentDateAfter(User user, MailType mailType, Instant sentAfter);
}
