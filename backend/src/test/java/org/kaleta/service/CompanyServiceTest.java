package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.kaleta.persistence.api.CompanyDao;


@QuarkusTest
public class CompanyServiceTest
{
    @InjectMock
    CompanyDao companyDao;

    @Inject
    CompanyService companyService;


}
