package example.org.nightout.service;

import example.org.nightout.dto.EventView;
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
}
