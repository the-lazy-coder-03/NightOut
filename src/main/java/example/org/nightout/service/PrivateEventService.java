package example.org.nightout.service;

import example.org.nightout.config.AppProperties;
import example.org.nightout.dto.PrivateEventView;
import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventMembership;
import example.org.nightout.exception.BusinessRuleException;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.repository.PrivateEventMembershipRepository;
import example.org.nightout.repository.PrivateEventRepository;
import example.org.nightout.security.AuthenticatedUser;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class PrivateEventService {

    private static final int JOIN_CODE_BOUND = 100_000_000;
    private static final int MAX_JOIN_CODE_ATTEMPTS = 25;

    private final PrivateEventRepository privateEventRepository;
    private final PrivateEventMembershipRepository membershipRepository;
    private final UserManagementService userManagementService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PrivateEventService(PrivateEventRepository privateEventRepository, PrivateEventMembershipRepository membershipRepository,
                               UserManagementService userManagementService, PasswordEncoder passwordEncoder, AppProperties properties, Clock clock) {
        this.privateEventRepository = privateEventRepository;
        this.membershipRepository = membershipRepository;
        this.userManagementService = userManagementService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PrivateEventView> eventsFor(AuthenticatedUser principal) {
        AppUser user = userManagementService.requireUser(principal.getId());
        return membershipRepository.findByUserOrderByJoinedAtDesc(user).stream()
                .map(membership -> viewFor(membership.getPrivateEvent(), true))
                .filter(view -> !view.expired() && !view.event().isCancelled())
                .toList();
    }

    @Transactional(readOnly = true)
    public PrivateEvent requireByJoinCode(String joinCode) {
        return privateEventRepository.findByJoinCode(normalizeJoinCode(joinCode))
                .orElseThrow(() -> new ResourceNotFoundException("Private event not found."));
    }

    @Transactional(readOnly = true)
    public PrivateEventView viewForJoinCode(AuthenticatedUser principal, String joinCode) {
        PrivateEvent event = requireByJoinCode(joinCode);
        if (expired(event) || event.isCancelled()) {
            throw new BusinessRuleException("This private event has expired.");
        }
        AppUser user = userManagementService.requireUser(principal.getId());
        return viewFor(event, membershipRepository.existsByPrivateEventAndUser(event, user));
    }

    @Transactional(readOnly = true)
    public PrivateEventView requireAccessible(AuthenticatedUser principal, String joinCode) {
        PrivateEvent event = requireByJoinCode(joinCode);
        if (expired(event) || event.isCancelled()) {
            throw new BusinessRuleException("This private event has expired.");
        }
        AppUser user = userManagementService.requireUser(principal.getId());
        if (!membershipRepository.existsByPrivateEventAndUser(event, user)) {
            throw new BusinessRuleException("Join this private event first.");
        }
        return viewFor(event, true);
    }

    @Transactional
    public PrivateEvent create(AuthenticatedUser principal, String eventName, LocalDate eventDate,
                               LocalTime startTime, LocalTime endTime, String location, String password) {
        AppUser creator = userManagementService.requireUser(principal.getId());
        LocalDate today = LocalDate.now(clock);
        if (eventDate.isBefore(today)) {
            throw new BusinessRuleException("Private event date cannot be in the past.");
        }
        if (password == null || password.length() < 8) {
            throw new BusinessRuleException("Private event password must be at least 8 characters.");
        }

        PrivateEvent event = new PrivateEvent();
        event.setCreator(creator);
        event.setEventName(requireText(eventName, "Event name is required."));
        event.setEventDate(eventDate);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setLocation(blankToNull(location));
        event.setJoinCode(generateJoinCode());
        event.setPasswordHash(passwordEncoder.encode(password));

        PrivateEvent saved = privateEventRepository.save(event);
        addMember(saved, creator);
        return saved;
    }

    @Transactional
    public PrivateEvent join(AuthenticatedUser principal, String joinCode, String password) {
        PrivateEvent event = requireByJoinCode(joinCode);
        if (expired(event) || event.isCancelled()) {
            throw new BusinessRuleException("This private event has expired.");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, event.getPasswordHash())) {
            throw new BusinessRuleException("Private event code or password is incorrect.");
        }
        AppUser user = userManagementService.requireUser(principal.getId());
        if (!membershipRepository.existsByPrivateEventAndUser(event, user)) {
            addMember(event, user);
        }
        return event;
    }

    public LocalDate expiresOn(PrivateEvent event) {
        return event.getEventDate().plusDays(properties.getRetentionDays());
    }

    private PrivateEventView viewFor(PrivateEvent event, boolean member) {
        return new PrivateEventView(event, expiresOn(event), expired(event), member);
    }

    private boolean expired(PrivateEvent event) {
        return LocalDate.now(clock).isAfter(expiresOn(event));
    }

    private void addMember(PrivateEvent event, AppUser user) {
        PrivateEventMembership membership = new PrivateEventMembership();
        membership.setPrivateEvent(event);
        membership.setUser(user);
        membershipRepository.save(membership);
    }

    private String generateJoinCode() {
        for (int attempt = 0; attempt < MAX_JOIN_CODE_ATTEMPTS; attempt++) {
            String code = String.format("%08d", secureRandom.nextInt(JOIN_CODE_BOUND));
            if (!privateEventRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new BusinessRuleException("Could not generate a unique private event code. Try again.");
    }

    private static String normalizeJoinCode(String joinCode) {
        String normalized = requireText(joinCode, "Private event code is required.").replaceAll("\\s+", "");
        if (!normalized.matches("\\d{8}")) {
            throw new ResourceNotFoundException("Private event not found.");
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
