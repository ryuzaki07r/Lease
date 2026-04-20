package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.GuestDTO;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Exceptions.UnauthorizedException;
import com.rahul.Lease.Models.Guest;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.GuestRepository;
import com.rahul.Lease.Services.Interfaces.GuestService;
import com.rahul.Lease.Utils.AppUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.stream.Collectors;

import static com.rahul.Lease.Utils.AppUtils.getCurrentUser;

@Service
@AllArgsConstructor
@Slf4j
public class GuestServiceImpl implements GuestService {
    private final GuestRepository guestRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<GuestDTO> getAllGuests() {
        User user = getCurrentUser();
        log.info("Fetching all guests of user with id: {}", user.getId());
        List<GuestDTO> guests = guestRepository.findByUser(user);
        return guests.stream()
                .map(guest -> modelMapper.map(guest, GuestDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public void updateGuest(Long guestId, GuestDTO guestDto) {
        log.info("Updating guest with id: {}", guestId);
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow( () -> new ResourceNotFoundException("Guest not found"));
        User user = getCurrentUser();
        if(!user.equals(guest.getUser())){
            throw new UnauthorizedException("you are not the owner of guest");
        }
        modelMapper.map(guestDto,guest);
        guest.setUser(user);
        guest.setId(guestId);

        guestRepository.save(guest);

        log.info("Guest with id : {} updated successfully", guestId);
    }

    @Override
    public void deleteGuest(Long guestId) {
        log.info("Deleting guest with id : {}", guestId);
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found"));
        User user = getCurrentUser();
        if(!user.equals(guest.getUser())){
            throw new UnauthorizedException("you are not the owner of the guest");
        }
        guestRepository.deleteById(guestId);
        log.info("Guest with id: {} deleted successfully", guestId);
    }

    @Override
    public GuestDTO addNewGuest(GuestDTO guestDto) {
        log.info("Adding new guest {}", guestDto);
        User user = getCurrentUser();
        Guest guest = modelMapper.map(guestDto, Guest.class);
        guest.setUser(user);

        Guest savedGuest = guestRepository.save(guest);
        log.info("Guest added with id: {}", savedGuest.getId());
        return modelMapper.map(savedGuest, GuestDTO.class);
    }
}
