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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
public class PrivateEventService {

    private static final int JOIN_CODE_BOUND = 100_000;
    private static final int MAX_JOIN_CODE_ATTEMPTS = 25;
    private static final int MAX_INVITE_TOKEN_ATTEMPTS = 25;
    private static final LocalTime DEFAULT_START_TIME = LocalTime.MIDNIGHT;
    private static final LocalTime DEFAULT_END_TIME = LocalTime.of(23, 59);

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
        return privateEventRepository.findVisibleToUser(user).stream()
                .map(event -> viewFor(event, true, isCreator(event, user)))
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
        return viewFor(event, isParticipant(event, user), isCreator(event, user));
    }

    @Transactional(readOnly = true)
    public PrivateEventView requireAccessible(AuthenticatedUser principal, String joinCode) {
        PrivateEvent event = requireByJoinCode(joinCode);
        if (expired(event) || event.isCancelled()) {
            throw new BusinessRuleException("This private event has expired.");
        }
        AppUser user = userManagementService.requireUser(principal.getId());
        if (!isParticipant(event, user)) {
            throw new BusinessRuleException("Join this private event first.");
        }
        return viewFor(event, true, isCreator(event, user));
    }

    @Transactional
    public PrivateEvent create(AuthenticatedUser principal, String eventName, String password, boolean guestSharingAllowed) {
        AppUser creator = userManagementService.requireUser(principal.getId());
        LocalDate today = LocalDate.now(clock);
        if (password == null || password.length() < 8) {
            throw new BusinessRuleException("Private event password must be at least 8 characters.");
        }

        PrivateEvent event = new PrivateEvent();
        event.setCreator(creator);
        event.setEventName(requireText(eventName, "Event name is required."));
        event.setEventDate(today);
        event.setStartTime(DEFAULT_START_TIME);
        event.setEndTime(DEFAULT_END_TIME);
        event.setJoinCode(generateJoinCode());
        event.setInviteToken(generateInviteToken());
        event.setPasswordHash(passwordEncoder.encode(password));
        event.setSharePassword(password);
        event.setGuestSharingAllowed(guestSharingAllowed);
        event.setCreatedAt(Instant.now(clock));

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

    @Transactional
    public PrivateEvent joinByInviteToken(AuthenticatedUser principal, String inviteToken) {
        PrivateEvent event = privateEventRepository.findByInviteToken(requireText(inviteToken, "Invite link is not valid."))
                .orElseThrow(() -> new ResourceNotFoundException("Private event not found."));
        if (expired(event) || event.isCancelled()) {
            throw new BusinessRuleException("This private event has expired.");
        }
        AppUser user = userManagementService.requireUser(principal.getId());
        if (!isParticipant(event, user)) {
            addMember(event, user);
        }
        return event;
    }

    public LocalDate expiresOn(PrivateEvent event) {
        Instant createdAt = event.getCreatedAt();
        LocalDate createdDate = createdAt == null
                ? event.getEventDate()
                : LocalDate.ofInstant(createdAt, ZoneId.of(properties.getTimeZone()));
        return createdDate.plusDays(properties.getPrivateEventRetentionDays());
    }

    private PrivateEventView viewFor(PrivateEvent event, boolean member, boolean creator) {
        return new PrivateEventView(event, expiresOn(event), expired(event), member, creator);
    }

    private boolean expired(PrivateEvent event) {
        return LocalDate.now(clock).isAfter(expiresOn(event));
    }

    private boolean isParticipant(PrivateEvent event, AppUser user) {
        if (isCreator(event, user)) {
            return true;
        }
        return membershipRepository.existsByPrivateEventAndUser(event, user);
    }

    private static boolean isCreator(PrivateEvent event, AppUser user) {
        return Objects.equals(event.getCreator().getId(), user.getId());
    }

    private void addMember(PrivateEvent event, AppUser user) {
        PrivateEventMembership membership = new PrivateEventMembership();
        membership.setPrivateEvent(event);
        membership.setUser(user);
        membershipRepository.save(membership);
    }

    private String generateJoinCode() {
        for (int attempt = 0; attempt < MAX_JOIN_CODE_ATTEMPTS; attempt++) {
            String code = String.format("%05d", secureRandom.nextInt(JOIN_CODE_BOUND));
            if (!privateEventRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new BusinessRuleException("Could not generate a unique private event code. Try again.");
    }

    private String generateInviteToken() {
        for (int attempt = 0; attempt < MAX_INVITE_TOKEN_ATTEMPTS; attempt++) {
            byte[] randomBytes = new byte[24];
            secureRandom.nextBytes(randomBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            if (!privateEventRepository.existsByInviteToken(token)) {
                return token;
            }
        }
        throw new BusinessRuleException("Could not generate a unique private event invite link. Try again.");
    }

    private static String normalizeJoinCode(String joinCode) {
        String normalized = requireText(joinCode, "Private event code is required.").replaceAll("\\s+", "");
        if (!normalized.matches("\\d{5}")) {
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

}
