/**
 * Spring Data JPA repositories.
 * <p>
 * The only layer that talks to the database. Queries stay here so that no JPQL,
 * SQL, or {@code EntityManager} usage leaks into services or controllers.
 */
package com.oae.fakka.repository;
