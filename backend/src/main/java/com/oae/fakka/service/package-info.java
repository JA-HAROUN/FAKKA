/**
 * Application services: business rules and the balance engine.
 * <p>
 * Services own the domain invariants (BR-1 to BR-7) and report expected failures by
 * throwing a {@link com.oae.fakka.exception.ApiException} subclass. They take and
 * return DTOs or entities, never {@code HttpServletRequest} or {@code ResponseEntity}.
 */
package com.oae.fakka.service;
