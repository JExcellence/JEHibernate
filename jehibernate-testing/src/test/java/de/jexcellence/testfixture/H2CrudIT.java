package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.testing.JEHibernateTest;
import de.jexcellence.jehibernate.testing.TestDatabase;

/** CRUD scenario against in-memory H2 (always runs — no Docker required). */
@JEHibernateTest(database = TestDatabase.H2, scanPackages = "de.jexcellence.testfixture")
class H2CrudIT extends AbstractCrudAcrossDatabasesIT {
}
