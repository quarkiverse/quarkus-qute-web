package org.acme;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@Named
@ApplicationScoped
public class PersonRepo implements PanacheRepository<Person> {

    public List<Person> listAllSortedByName() {
        return listAll(Sort.ascending("surname", "name"));
    }

}
