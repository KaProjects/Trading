package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Estimate;

import java.util.List;
import java.util.Optional;

public interface EstimateDao extends EntityDao<Estimate>
{
    /**
     * @return estimates of the specified type for the specified period
     */
    List<Estimate> list(Long periodId, boolean type);

    /**
     * @return latest estimate of the specified type for the specified period
     */
    Optional<Estimate> findLatest(Long periodId, boolean type);

    /**
     * @return latest estimate of the specified type for each specified period
     */
    List<Estimate> findLatestByPeriodIds(List<Long> periodIds, boolean type);
}
