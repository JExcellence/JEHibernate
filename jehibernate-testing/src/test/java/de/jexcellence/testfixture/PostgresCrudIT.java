package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.testing.JEHibernateTest;
import de.jexcellence.jehibernate.testing.TestDatabase;

/** CRUD scenario against PostgreSQL via Testcontainers (skipped without Docker). */
@JEHibernateTest(database = TestDatabase.POSTGRES, scanPackages = "de.jexcellence.testfixture")
class PostgresCrudIT extends AbstractCrudAcrossDatabasesIT {
}
