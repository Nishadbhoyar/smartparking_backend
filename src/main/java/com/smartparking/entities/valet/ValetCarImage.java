package com.smartparking.entities.valet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "valet_car_images")
public class ValetCarImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valet_request_id", nullable = false)
    private ValetRequest valetRequest;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    /**
     * Which phase of the valet job this photo was taken in.
     * "PICKUP"  — taken before the valet drives off (shows car condition at handover)
     * "PARKED"  — taken after parking the car (shows car in the spot)
     */
    @Column(name = "image_phase", nullable = false, length = 10)
    private String imagePhase ="PARKED";
}