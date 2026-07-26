package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.entity.Guest;
import com.sameerahmed.projects.airBnbApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    List<Guest> findByUser(User user);
}