package pro.kaleert.uwubot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.kaleert.uwubot.entity.SubjectAlias;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectAliasRepository extends JpaRepository<SubjectAlias, Long> {
    List<SubjectAlias> findAllByUserId(Long userId);
    Optional<SubjectAlias> findByUserIdAndOriginalName(Long userId, String originalName);
    void deleteByUserIdAndOriginalName(Long userId, String originalName);
}
