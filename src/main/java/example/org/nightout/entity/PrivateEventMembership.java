package example.org.nightout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "private_event_memberships",
        uniqueConstraints = @UniqueConstraint(name = "uk_private_event_membership", columnNames = {"private_event_id", "user_id"})
)
public class PrivateEventMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "private_event_id", nullable = false)
    private PrivateEvent privateEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public PrivateEvent getPrivateEvent() {
        return privateEvent;
    }

    public void setPrivateEvent(PrivateEvent privateEvent) {
        this.privateEvent = privateEvent;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
