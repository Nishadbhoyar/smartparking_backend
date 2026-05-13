package com.smartparking.service;

import com.smartparking.dtos.request.ValetBookingRequestDTO;
import com.smartparking.dtos.response.ValetResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ValetService {

    ValetResponseDTO getRequestById(Long requestId);

    ValetResponseDTO requestValet(ValetBookingRequestDTO requestDTO);

    List<ValetResponseDTO> getAvailableJobs();

    ValetResponseDTO getActiveJob(Long valetId);

    ValetResponseDTO acceptJob(Long requestId, Long valetId);

    ValetResponseDTO verifyPickup(Long requestId, String enteredOtp);

    // NEW: Upload photos taken at pickup (before driving to the lot).
    // Can be called when status is ACCEPTED or PICKED_UP.
    ValetResponseDTO uploadPickupImages(Long requestId, List<MultipartFile> images,
                                        Integer batteryLevelAtPickup);

    ValetResponseDTO parkVehicle(Long requestId, Long lotId, Long slotId,
                                 List<MultipartFile> carImages, Integer batteryLevelAtParking);

    // NEW: Generates a returnConfirmOtp and moves status to RETURN_CONFIRM_PENDING.
    // The customer must confirm this OTP within 5 minutes.
    ValetResponseDTO initiateReturnConfirmation(Long requestId);

    // NEW: Validates the returnConfirmOtp. If correct and not expired,
    // moves status to RETURN_REQUESTED and notifies the valet.
    ValetResponseDTO confirmReturn(Long requestId, String otp);

    ValetResponseDTO verifyDropoff(Long requestId, String enteredOtp);

    ValetResponseDTO getActiveValetForCustomer(Long customerId);

    List<ValetResponseDTO> getCustomerValetHistory(Long customerId);
}
