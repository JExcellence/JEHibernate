package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.testing.JEHibernateTest;
import de.jexcellence.jehibernate.testing.TestDatabase;

/** CRUD scenario against MariaDB via Testcontainers (skipped without Docker). */
@JEHibernateTest(database = TestDatabase.MARIADB, scanPackages = "de.jexcellence.testfixture")
class MariaDbCrudIT extends AbstractCrudAcrossDatabasesIT {
}
