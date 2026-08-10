package example.org.nightout.service;

import example.org.nightout.dto.EventView;
import example.org.nightout.dto.NightDateView;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.PhotoStatus;
import example.org.nightout.exception.ResourceNotFoundException;
import example.org.nightout.repository.NightEventRepository;
import example.org.nightout.repository.PhotoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final NightEventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final ClubService clubService;
    private final EventPolicyService policyService;

    public EventService(NightEventRepository eventRepository, PhotoRepository photoRepository, ClubService clubService, EventPolicyService policyService) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.clubService = clubService;
        this.policyService = policyService;
    }

    @Transactional(readOnly = true)
    public List<EventView> eventViewsForClub(Club club) {
        return eventRepository.findByClubAndCancelledFalseOrderByEventDateDesc(club).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NightDateView> nightDateViewsForClub(Club club, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return List.of();
        }
        LocalDate endDate = dates.getFirst();
        LocalDate startDate = dates.getLast();
        Map<LocalDate, List<EventView>> eventsByDate = eventRepository
                .findByClubAndCancelledFalseAndEventDateBetweenOrderByEventDateDescStartTimeAsc(club, startDate, endDate)
                .stream()
                .map(this::toView)
                .collect(Collectors.groupingBy(view -> view.event().getEventDate()));

        return dates.stream()
                .map(date -> {
                    List<EventView> eventViews = eventsByDate.getOrDefault(date, List.of());
                    long photoCount = eventViews.stream().mapToLong(EventView::photoCount).sum();
                    return new NightDateView(date, date.equals(endDate), eventViews, photoCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventView> eventViewsForClubAndDate(Club club, LocalDate date) {
        return eventRepository.findByClubAndCancelledFalseAndEventDateOrderByStartTimeAsc(club, date).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean uploadAvailableForDate(LocalDate date) {
        return policyService.uploadAvailable(date);
    }

    @Transactional
    public NightEvent uploadTargetForClubDate(String clubSlug, LocalDate date, Long eventId) {
        if (eventId != null) {
            NightEvent event = requirePublicEvent(clubSlug, eventId);
            if (!event.getEventDate().equals(date)) {
                throw new ResourceNotFoundException("Night not found.");
            }
            return event;
        }

        Club club = clubService.requireActiveBySlug(clubSlug);
        return eventRepository.findByClubAndCancelledFalseAndEventDateOrderByStartTimeAsc(club, date).stream()
                .filter(policyService::uploadAvailable)
                .findFirst()
                .orElseGet(() -> createDefaultUploadEvent(club, date));
    }

    @Transactional(readOnly = true)
    public NightEvent requirePublicEvent(String clubSlug, Long eventId) {
        return eventRepository.findByIdAndClubSlug(eventId, clubSlug)
                .filter(event -> event.getClub().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Night not found."));
    }

    @Transactional(readOnly = true)
    public NightEvent requireById(Long eventId) {
        return eventRepository.findWithClubById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Night not found."));
    }

    @Transactional(readOnly = true)
    public EventView viewFor(NightEvent event) {
        return toView(event);
    }

    @Transactional
    public NightEvent create(Long clubId, String eventName, LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        Club club = clubService.requireById(clubId);
        NightEvent event = new NightEvent();
        event.setClub(club);
        apply(event, eventName, eventDate, startTime, endTime);
        return eventRepository.save(event);
    }

    @Transactional
    public NightEvent update(Long eventId, String eventName, LocalDate eventDate, LocalTime startTime, LocalTime endTime, boolean cancelled) {
        NightEvent event = requireById(eventId);
        apply(event, eventName, eventDate, startTime, endTime);
        event.setCancelled(cancelled);
        return eventRepository.save(event);
    }

    @Transactional
    public void cancel(Long eventId) {
        requireById(eventId).setCancelled(true);
    }

    @Transactional
    public void delete(Long eventId) {
        eventRepository.delete(requireById(eventId));
    }

    private EventView toView(NightEvent event) {
        return new EventView(
                event,
                policyService.expiresOn(event),
                policyService.statusFor(event),
                photoRepository.countByEventAndStatus(event, PhotoStatus.APPROVED),
                policyService.uploadAvailable(event),
                policyService.galleryAvailable(event)
        );
    }

    private static void apply(NightEvent event, String eventName, LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("Event name is required.");
        }
        event.setEventName(eventName.trim());
        event.setEventDate(eventDate);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
    }

    private NightEvent createDefaultUploadEvent(Club club, LocalDate date) {
        policyService.requireUploadAvailable(date);
        NightEvent event = new NightEvent();
        event.setClub(club);
        apply(event, "Night Out", date, LocalTime.of(22, 0), LocalTime.of(3, 0));
        return eventRepository.save(event);
    }
}
