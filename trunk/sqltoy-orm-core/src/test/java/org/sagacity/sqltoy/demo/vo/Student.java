package org.sagacity.sqltoy.demo.vo;

import java.time.LocalDate;

public record Student(String id,String name,Integer age,LocalDate birthDay) {};
