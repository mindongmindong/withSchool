package com.withSchool.service.subject;

import com.withSchool.dto.file.FileDTO;
import com.withSchool.dto.file.FileDeleteDTO;
import com.withSchool.dto.subject.ReqSubjectLectureNoteDTO;
import com.withSchool.dto.subject.ResSubjectLectureNoteDTO;
import com.withSchool.dto.user.ResUserDefaultDTO;
import com.withSchool.entity.subject.SubjectLectureNote;
import com.withSchool.entity.subject.Subject;
import com.withSchool.entity.user.User;
import com.withSchool.entity.subject.SubjectLectureNoteFile;
import com.withSchool.repository.mapping.StudentSubjectRepository;
import com.withSchool.repository.subject.SubjectLectureNoteRepository;
import com.withSchool.repository.subject.SubjectRepository;
import com.withSchool.repository.file.SubjectLectureNoteFileRepository;
import com.withSchool.service.file.FileService;
import com.withSchool.service.mapping.StudentSubjectService;
import com.withSchool.service.user.NotificationService;
import com.withSchool.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class SubjectLectureNoteService {

    private final SubjectLectureNoteRepository subjectLectureNoteRepository;
    private final SubjectLectureNoteFileRepository subjectLectureNoteFileRepository;
    private final SubjectRepository subjectRepository;
    private final FileService fileService;
    private final UserService userService;
    private final StudentSubjectRepository studentSubjectRepository;
    private final StudentSubjectService studentSubjectService;
    private final NotificationService notificationService;

    @Transactional
    public List<ResSubjectLectureNoteDTO> getAllSubjectLectureNotes() {
        User user = userService.getCurrentUser();
        List<Subject> subjectList = studentSubjectRepository.findSubjectsByUser(user);
        List<ResSubjectLectureNoteDTO> allSubjectLectureNotes = new ArrayList<>();

        for (Subject subject : subjectList) {
            List<SubjectLectureNote> subjectLectureNotes = subjectLectureNoteRepository.findBySubject(subject).orElseThrow(NoSuchElementException::new);
            for (SubjectLectureNote subjectLectureNote : subjectLectureNotes) {
                allSubjectLectureNotes.add(mapToSubjectLectureNoteDTO(subjectLectureNote));
            }
        }
        return allSubjectLectureNotes;
    }
    @Transactional
    public List<ResSubjectLectureNoteDTO> getAllSubjectLectureNotesBySubject(Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId).orElseThrow(NoSuchElementException::new);
        List<SubjectLectureNote> subjectLectureNotes = subjectLectureNoteRepository.findBySubject(subject).orElseThrow(NoSuchElementException::new);
        List<ResSubjectLectureNoteDTO> allSubjectLectureNotes = new ArrayList<>();
        for (SubjectLectureNote subjectLectureNote : subjectLectureNotes) {
            allSubjectLectureNotes.add(mapToSubjectLectureNoteDTO(subjectLectureNote));
        }
        return allSubjectLectureNotes;
    }

    @Transactional
    public ResSubjectLectureNoteDTO getSubjectLectureNoteById(Long id) {
        SubjectLectureNote subjectLectureNote = subjectLectureNoteRepository.findById(id).orElseThrow(NoSuchElementException::new);
        return mapToSubjectLectureNoteDTO(subjectLectureNote);
    }

    @Transactional
    public ResSubjectLectureNoteDTO createSubjectLectureNote(ReqSubjectLectureNoteDTO reqSubjectLectureNoteDTO) {
        Optional<Subject> optionalSubject = subjectRepository.findById(reqSubjectLectureNoteDTO.getSubjectId());
        if (optionalSubject.isPresent()) {
            String title = reqSubjectLectureNoteDTO.getTitle();
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("Title cannot be null or empty");
            }

            // Log title value
//            System.out.println("Creating SubjectLectureNote with title: " + title);

            Subject subject = optionalSubject.get();
//            System.out.println("Subject: " + subject.getSubjectId().toString());
            User currentUser = userService.getCurrentUser();

            SubjectLectureNote subjectLectureNote = new SubjectLectureNote();
            subjectLectureNote.setTitle(title);
            subjectLectureNote.setSubject(subject);
            subjectLectureNote.setUser(currentUser);

            // Save SubjectLectureNote
            SubjectLectureNote savedSubjectLectureNote = subjectLectureNoteRepository.save(subjectLectureNote);

            // Log SubjectLectureNote creation
//            System.out.println("Created SubjectLectureNote: " + savedSubjectLectureNote.toString());

            // Save files to S3 and update database
            saveFiles(reqSubjectLectureNoteDTO.getFile(), savedSubjectLectureNote.getSubjectLectureNoteId());

            List<User> userList = studentSubjectService.findSugangStudent(reqSubjectLectureNoteDTO.getSubjectId());
            notificationService.sendSMSGroup(userList, "과목 강의노트가", reqSubjectLectureNoteDTO.getTitle(), true);

            return mapToSubjectLectureNoteDTO(savedSubjectLectureNote);
        } else {
            throw new IllegalArgumentException("Subject not found");
        }
    }


    @Transactional
    public ResSubjectLectureNoteDTO updateLectureNote(Long id, ReqSubjectLectureNoteDTO reqSubjectLectureNoteDTO) {
        SubjectLectureNote existingSubjectLectureNote = subjectLectureNoteRepository.findById(id)
                .orElseThrow(NoSuchElementException::new);

        // 필요한 모든 연관 엔티티 초기화
        Hibernate.initialize(existingSubjectLectureNote.getSubject());
        Hibernate.initialize(existingSubjectLectureNote.getUser());

        Optional<Subject> optionalSubject = subjectRepository.findById(reqSubjectLectureNoteDTO.getSubjectId());

        if (optionalSubject.isPresent()) {
            Subject subject = optionalSubject.get();

            existingSubjectLectureNote.setSubject(subject);
            existingSubjectLectureNote.setTitle(reqSubjectLectureNoteDTO.getTitle());

            SubjectLectureNote updatedSubjectLectureNote = subjectLectureNoteRepository.save(existingSubjectLectureNote);

            deleteFilesByLectureNoteId(id);
            saveFiles(reqSubjectLectureNoteDTO.getFile(), id);

            return mapToSubjectLectureNoteDTO(updatedSubjectLectureNote);
        } else {
            throw new IllegalArgumentException("Subject not found");
        }
    }


    @Transactional
    public void deleteLectureNote(Long id) {
        SubjectLectureNote subjectLectureNote = subjectLectureNoteRepository.findById(id)
                .orElseThrow(NoSuchElementException::new);

        deleteFilesByLectureNoteId(id);
        subjectLectureNoteRepository.delete(subjectLectureNote);
    }

    @Transactional
    public ResSubjectLectureNoteDTO mapToSubjectLectureNoteDTO(SubjectLectureNote subjectLectureNote) {

        Hibernate.initialize(subjectLectureNote.getUser());

        ResUserDefaultDTO userDTO = ResUserDefaultDTO.builder()
                .userName(subjectLectureNote.getUser().getId())
                .name(subjectLectureNote.getUser().getName())
                .userId(subjectLectureNote.getUser().getUserId())
                .build();

        List<SubjectLectureNoteFile> files = subjectLectureNoteFileRepository.findBySubjectLectureNote_SubjectLectureNoteId(subjectLectureNote.getSubjectLectureNoteId()).orElse(new ArrayList<>());
        List<String> fileUrls = new ArrayList<>();
        List<String> originalNames = new ArrayList<>();
        for (SubjectLectureNoteFile file : files) {
            fileUrls.add(file.getFileUrl());
            originalNames.add(file.getOriginalName());
        }

        return ResSubjectLectureNoteDTO.builder()
                .subjectLectureNoteId(subjectLectureNote.getSubjectLectureNoteId())
                .title(subjectLectureNote.getTitle())
                .user(userDTO)
                .regDate(subjectLectureNote.getRegDate())
                .filesURl(fileUrls)
                .originalName(originalNames)
                .build();
    }

    private void saveFiles(List<MultipartFile> files, Long lectureNoteId) {

        if(files != null && !files.isEmpty()){
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    FileDTO fileDTO = FileDTO.builder()
                            .file(file)
                            .repoType("subjectLectureNote")
                            .masterId(lectureNoteId)
                            .build();
                    fileService.saveFile(fileDTO);
                }
            }
        }
    }

    private void deleteFilesByLectureNoteId(Long lectureNoteId) {
        Optional<List<SubjectLectureNoteFile>> files = subjectLectureNoteFileRepository.findBySubjectLectureNote_SubjectLectureNoteId(lectureNoteId);
        if (files.isPresent()) {
            for (SubjectLectureNoteFile file : files.get()) {
                FileDeleteDTO fileDeleteDTO = FileDeleteDTO.builder()
                        .savedName(file.getSavedName())
                        .repoType("subjectLectureNote")
                        .masterId(lectureNoteId)
                        .build();
                fileService.deleteSubjectLectureNoteFile(fileDeleteDTO);
            }
        }
    }
}
