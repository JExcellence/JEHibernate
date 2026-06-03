package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.testing.JEHibernateTest;
import de.jexcellence.jehibernate.testing.TestDatabase;

/** CRUD scenario against MySQL via Testcontainers (skipped without Docker). */
@JEHibernateTest(database = TestDatabase.MYSQL, scanPackages = "de.jexcellence.testfixture")
class MySqlCrudIT extends AbstractCrudAcrossDatabasesIT {
}
