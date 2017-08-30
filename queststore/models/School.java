package queststore.models;

import java.util.ArrayList;
import queststore.models.User;
import queststore.dao.ClassDao;
import queststore.dao.ManagerDao;
import queststore.dao.MentorDao;
import queststore.dao.StudentDao;

public class School{
    private String name;
    private ClassDao classDao;
    private ManagerDao managerDao;
    private MentorDao mentorDao;
    private StudentDao studentDao;

    public School(String name){
        this.name = name;
        this.managerDao = new ManagerDao();
        this.mentorDao = new MentorDao();
        this.studentDao = new StudentDao();
        this.classDao = new ClassDao();
    }

}