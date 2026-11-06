package team.projectpulse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<PeerEvaluationUser, Integer> {

    Optional<PeerEvaluationUser> findByUsername(String username);

    Optional<PeerEvaluationUser> findByEmail(String email);

    List<PeerEvaluationUser> findByEmailIn(Collection<String> emails);

    /**
     * Is this an existing, active (not deactivated) account? Read on every Bearer-authenticated request
     * (FR-SEC-active-account), so it is an existence query by primary key with no entity load.
     */
    boolean existsByIdAndEnabledTrue(Integer id);

}
