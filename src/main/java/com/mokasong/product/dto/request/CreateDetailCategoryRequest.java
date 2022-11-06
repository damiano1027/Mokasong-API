package com.mokasong.product.dto.request;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Getter @Setter
public class CreateDetailCategoryRequest {
    @NotBlank @Size(max = 15)
    private String name;
    @NotNull @Positive
    private Long rootCategoryId;
}
