package pro.kaleert.uwubot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.kaleert.uwubot.entity.ParsingMeta;

@Repository
public interface ParsingMetaRepository extends JpaRepository<ParsingMeta, String> {
}
