package org.acme;

import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class Person extends PanacheEntity {

    public String name;
    
    public String surname;
    
    public LocalDate dateOfBirth;
}