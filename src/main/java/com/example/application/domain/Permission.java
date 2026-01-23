package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Represents a discrete permission/capability in the system. Permissions are assigned to roles,
 * which are then assigned to users.
 */
@Entity
@Table(name = "permission")
public class Permission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 50)
  @Column(nullable = false, unique = true, length = 50)
  private String name;

  @Size(max = 255)
  @Column(length = 255)
  private String description;

  @Size(max = 50)
  @Column(name = "category", length = 50)
  private String category;

  // Constructors
  public Permission() {}

  public Permission(String name, String description) {
    this.name = name;
    this.description = description;
  }

  public Permission(String name, String description, String category) {
    this.name = name;
    this.description = description;
    this.category = category;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Permission)) return false;
    Permission that = (Permission) o;
    return name != null && name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name != null ? name.hashCode() : 0;
  }
}
