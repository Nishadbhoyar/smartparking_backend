package com.smartparking.service.Impl;

import com.smartparking.dtos.request.FeedbackRequestDTO;
import com.smartparking.dtos.response.FeedbackResponseDTO;
import com.smartparking.entities.parking.Feedback;
import com.smartparking.entities.parking.ParkingLot;
import com.smartparking.entities.users.Customer;
import com.smartparking.entities.valet.Valet;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.CustomerRepository;
import com.smartparking.repositories.FeedbackRepository;
import com.smartparking.repositories.ParkingLotRepository;
import com.smartparking.repositories.ValetRepository;
import com.smartparking.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ValetRepository valetRepository;

    @Override
    @Transactional
    public FeedbackResponseDTO submitFeedback(FeedbackRequestDTO requestDTO) {
        Customer customer = customerRepository.findById(requestDTO.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found with ID: " + requestDTO.getCustomerId()));

        if (requestDTO.getParkingLotId() == null && requestDTO.getValetId() == null) {
            throw new IllegalArgumentException(
                    "Feedback must be attached to either a Parking Lot or a Valet.");
        }

        Feedback feedback = null;

        // 1. Check if rating a Parking Lot
        if (requestDTO.getParkingLotId() != null) {
            ParkingLot lot = parkingLotRepository.findById(requestDTO.getParkingLotId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found!"));

            // Try to find an existing review
            feedback = feedbackRepository.findFirstByCustomerIdAndParkingLotIdOrderByCreatedAtDesc(
                    customer.getId(), lot.getId());

            // If no review exists, create a new one
            if (feedback == null) {
                feedback = new Feedback();
                feedback.setCustomer(customer);
                feedback.setParkingLot(lot);
            }
        }

        // 2. Check if rating a Valet
        else if (requestDTO.getValetId() != null) {
            Valet valet = valetRepository.findById(requestDTO.getValetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Valet not found!"));

            // Try to find an existing review
            feedback = feedbackRepository.findFirstByCustomerIdAndValetIdOrderByCreatedAtDesc(
                    customer.getId(), valet.getId());

            // If no review exists, create a new one
            if (feedback == null) {
                feedback = new Feedback();
                feedback.setCustomer(customer);
                feedback.setValet(valet);
            }
        }

        // 3. Apply the new rating and comment (updates existing OR populates new)
        feedback.setRating(requestDTO.getRating());
        feedback.setComment(requestDTO.getComment());

        // 4. Save to database
        Feedback savedFeedback = feedbackRepository.save(feedback);
        return mapToResponseDTO(savedFeedback);
    }

    @Override
    @Transactional(readOnly = true) // FIX #4a: lazy customer/parkingLot/valet need open session
    public List<FeedbackResponseDTO> getLotFeedback(Long lotId) {
        return feedbackRepository.findByParkingLotIdOrderByCreatedAtDesc(lotId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true) // FIX #4b: lazy customer/parkingLot/valet need open session
    public List<FeedbackResponseDTO> getValetFeedback(Long valetId) {
        return feedbackRepository.findByValetIdOrderByCreatedAtDesc(valetId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Double getLotAverageRating(Long lotId) {
        Double avg = feedbackRepository.getAverageRatingForParkingLot(lotId);
        if (avg == null) return 0.0;
        return Math.round(avg * 10.0) / 10.0;
    }

    @Override
    public Double getValetAverageRating(Long valetId) {
        Double avg = feedbackRepository.getAverageRatingForValet(valetId);
        if (avg == null) return 0.0;
        return Math.round(avg * 10.0) / 10.0;
    }

    private FeedbackResponseDTO mapToResponseDTO(Feedback feedback) {
        FeedbackResponseDTO dto = new FeedbackResponseDTO();
        dto.setId(feedback.getId());
        dto.setRating(feedback.getRating());
        dto.setComment(feedback.getComment());
        dto.setCreatedAt(feedback.getCreatedAt());
        dto.setCustomerName(feedback.getCustomer().getName());
        if (feedback.getParkingLot() != null) dto.setParkingLotId(feedback.getParkingLot().getId());
        if (feedback.getValet()      != null) dto.setValetId(feedback.getValet().getId());
        return dto;
    }

    // 🚨 ADD THIS METHOD AT THE BOTTOM OF FeedbackServiceImpl.java 🚨
    @Override
    @Transactional
    public void deleteFeedback(Long feedbackId) {
        if (!feedbackRepository.existsById(feedbackId)) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        feedbackRepository.deleteById(feedbackId);
    }
}