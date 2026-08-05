package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Estimate;

import java.util.List;
import java.util.Optional;

public interface EstimateDao extends EntityDao<Estimate>
{
    /**
     * @return estimates for the specified period
     */
    List<Estimate> list(Long periodId);

    /**
     * @return latest estimate for the specified period
     */
    Optional<Estimate> findLatest(Long periodId);

    /**
     * @return latest estimate for each specified period
     */
    List<Estimate> findLatestByPeriodIds(List<Long> periodIds);
}
