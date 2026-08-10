package example.org.nightout.service;

import example.org.nightout.entity.NightEvent;
import example.org.nightout.entity.Photo;
import example.org.nightout.repository.NightEventRepository;
import example.org.nightout.repository.PhotoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminQueryService {

    private final NightEventRepository eventRepository;
    private final PhotoRepository photoRepository;

    public AdminQueryService(NightEventRepository eventRepository, PhotoRepository photoRepository) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
    }

    @Transactional(readOnly = true)
    public List<NightEvent> allEvents() {
        return eventRepository.findAllByOrderByEventDateDesc();
    }

    @Transactional(readOnly = true)
    public List<Photo> allPhotos() {
        return photoRepository.findAllByOrderByUploadedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Photo> photosForClub(Long clubId) {
        return photoRepository.findByEvent_Club_IdOrderByUploadedAtDesc(clubId);
    }
}
