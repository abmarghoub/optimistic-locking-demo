package com.example.service;

import com.example.model.Reservation;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.Optional;

public class ReservationServiceImpl implements ReservationService {

    private final EntityManagerFactory emf;

    public ReservationServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public boolean hasOverlap(Reservation reservation) {
        EntityManager em = emf.createEntityManager();
        try {
            Long count = em.createQuery("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.salle = :salle
              AND r.id <> :id
              AND (
                    (r.dateDebut < :dateFin AND r.dateFin > :dateDebut)
                  )
            """, Long.class)
                    .setParameter("salle", reservation.getSalle())
                    .setParameter("id", reservation.getId() == null ? -1L : reservation.getId())
                    .setParameter("dateDebut", reservation.getDateDebut())
                    .setParameter("dateFin", reservation.getDateFin())
                    .getSingleResult();

            return count > 0;
        } finally {
            em.close();
        }
    }


    @Override
    public Reservation save(Reservation reservation) {

        if (hasOverlap(reservation)) {
            throw new IllegalArgumentException("Une autre réservation existe déjà sur cette période !");
        }

        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(reservation);
            em.getTransaction().commit();
            return reservation;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }



    @Override
    public Optional<Reservation> findById(Long id) {
        EntityManager em = emf.createEntityManager();
        try {
            Reservation reservation = em.find(Reservation.class, id);
            return Optional.ofNullable(reservation);
        } finally {
            em.close();
        }
    }

    @Override
    public void update(Reservation reservation) {

        if (hasOverlap(reservation)) {
            throw new IllegalArgumentException("Une autre réservation existe déjà sur cette période !");
        }

        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(reservation);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public void delete(Reservation reservation) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            if (!em.contains(reservation)) {
                reservation = em.merge(reservation);
            }
            em.remove(reservation);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }



}