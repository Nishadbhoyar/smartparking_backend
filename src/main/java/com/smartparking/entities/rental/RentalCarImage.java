package com.smartparking.entities.rental;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores car photos uploaded by the car owner.
 * Images are saved as raw bytes (LONGBLOB) in MySQL — same pattern as ValetCarImage.
 * The frontend fetches them via an authenticated axios call (not a plain <img src>),
 * so the JWT is validated before any bytes are returned.
 */
@Entity
@Getter
@Setter
@Table(name = "rental_car_images")
public class RentalCarImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_car_id", nullable = false)
    private RentalCar rentalCar;

    /** Raw binary image data stored in MySQL LONGBLOB. */
    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    /** MIME type: image/jpeg, image/png, image/webp */
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;
}