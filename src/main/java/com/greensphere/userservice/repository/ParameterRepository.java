package com.greensphere.userservice.repository;

import com.greensphere.userservice.entity.Parameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParameterRepository extends JpaRepository<Parameter, Long> {

    Parameter findParameterByName(String name);}
