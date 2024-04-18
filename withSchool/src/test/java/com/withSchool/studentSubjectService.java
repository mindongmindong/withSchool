package com.withSchool;

import com.withSchool.dto.StudentListDTO;
import com.withSchool.dto.SubjectInfoDTO;
import com.withSchool.entity.User;
import com.withSchool.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class studentSubjectService {

    @Autowired
    private StudentSubjectService studentSubjectService;

    @Autowired
    private UserService userService;

    @Autowired
    private SubjectService subjectService;


    @Test
    public void findOnesSugang() {
        User user = userService.findById("id1");

        System.out.println(studentSubjectService.findOnesSugang(user));
    }

    @Test
    public void sugangInformation(){
        Map<String, Object> response = new HashMap<>();
        Long subjectId = 2L;
        SubjectInfoDTO subjectInfoDTO = subjectService.findById(subjectId);
        List<User> users = studentSubjectService.findSugangStudent(subjectId);
        List<StudentListDTO> studentListDTOS = new ArrayList<>();

        for (User u : users) {
            StudentListDTO studentListDTO = StudentListDTO.builder()
                    .userId(u.getUserId())
                    .name(u.getName())
                    .id(u.getId())
                    .build();

            studentListDTOS.add(studentListDTO);
        }
        response.put("subject", subjectInfoDTO);
        response.put("students", studentListDTOS);

        System.out.println(response);
    }

}
