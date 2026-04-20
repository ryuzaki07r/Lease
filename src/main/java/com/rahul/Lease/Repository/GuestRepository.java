package com.rahul.Lease.Repository;

import com.rahul.Lease.Dto.GuestDTO;
import com.rahul.Lease.Models.Guest;
import com.rahul.Lease.Models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestRepository extends JpaRepository<Guest, Long> {
    List<GuestDTO> findByUser(User user);
}
