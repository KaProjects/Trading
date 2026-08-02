package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Estimate;

import java.util.List;

public interface EstimateDao extends EntityDao<Estimate>
{
    /**
     * @return estimates for the specified period
     */
    List<Estimate> list(Long periodId);
}
