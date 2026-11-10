package com.rahul.Lease.Dto;

import com.rahul.Lease.Enums.Gender;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GuestDTO {
    private Long id;
    @NotBlank
    private String name;
    @NotNull
    private Gender gender;
    @Min(0)
    private Integer age;
}
