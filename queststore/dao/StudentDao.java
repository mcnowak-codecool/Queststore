package queststore.dao;

import java.util.Scanner;
import java.util.ArrayList;
import java.io.FileNotFoundException;
import java.io.File;

import queststore.models.Student;
import queststore.models.Class;

public class StudentDao {

    private ArrayList<Student> students;

    public StudentDao(ClassDao classDao) {
        this.students = readStudentsData(classDao);
    }

    private ArrayList<Student> readStudentsData(ClassDao classDao) {
        ArrayList<Student> loadedStudents= new ArrayList<>();
        String[] studentData;
        Scanner fileScan;

        try {
            fileScan = new Scanner(new File("queststore/csv/student.csv"));

            while(fileScan.hasNextLine()) {
                studentData = fileScan.nextLine().split("\\|");
                String name = studentData[1];
                Integer id = Integer.parseInt(studentData[0]);
                String login = studentData[2];
                String password = studentData[3];
                String email = studentData[4];
                Student student = new Student(name, login, password, email, id);
                loadedStudents.add(student);

                Integer classId = Integer.parseInt(studentData[5]);
                Class clas = classDao.getClass(classId);
                clas.addStudent(student);

            }

        } catch (FileNotFoundException e) {
            System.out.println("File student.csv not found!");
        }

        return loadedStudents;
    }

    public Student getStudent(Integer id) {

        for (Student student : this.students) {
            if (student.getId() == id) {
                return student;
            }
        }

        return null;
    }

    public Student getStudent(String login) {

        for (Student student : this.students) {
            if (student.getLogin().equals(login)) {
                return student;
            }
        }

        return null;
    }

    public void save() {
    }
}
