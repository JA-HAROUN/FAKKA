/**
 * JPA entities — the persistence model.
 * <p>
 * Entities are never returned from a controller; map them to a DTO first so that the
 * database schema and the public API can evolve independently. Monetary amounts are
 * stored in integer minor units (piastres), never as floating point.
 */
package com.oae.fakka.entity;
