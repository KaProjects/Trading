package org.kaleta.persistence.api;

import org.kaleta.model.TargetStats;
import org.kaleta.persistence.entity.Target;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TargetDao extends EntityDao<Target>
{
    List<Target> list(Long periodId);

    List<Target> listByPeriodIds(List<Long> periodIds);

    Optional<Target> findByIdentity(
            Long periodId,
            Date date,
            String institution,
            BigDecimal price);

    Map<Long, TargetStats> statistics(List<Long> periodIds);

    void createAll(List<Target> targets);

    void delete(Long targetId);
}
