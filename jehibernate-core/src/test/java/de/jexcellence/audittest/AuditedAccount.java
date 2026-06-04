package de.jexcellence.audittest;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.concurrent.ExecutorService;

@Entity
@Table(name = "audited_account")
@Audited
public class AuditedAccount extends LongIdEntity {

    private String owner;
    private int balance;

    protected AuditedAccount() {}

    public AuditedAccount(String owner, int balance) {
        this.owner = owner;
        this.balance = balance;
    }

    public String getOwner() {
        return owner;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }
}

class AuditedAccountRepository extends AbstractCrudRepository<AuditedAccount, Long> {
    AuditedAccountRepository(ExecutorService executor, EntityManagerFactory emf, Class<AuditedAccount> entityClass) {
        super(executor, emf, entityClass);
    }
}
