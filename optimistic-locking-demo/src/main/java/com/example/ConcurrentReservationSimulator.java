package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;

import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import javax.persistence.LockModeType;

public class ConcurrentReservationSimulator {

    private static final EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("optimistic-locking-demo");
    private static final ReservationService reservationService = new ReservationServiceImpl(emf);




    public static void main(String[] args) throws InterruptedException {

        Scanner scanner = new Scanner(System.in);

        // Initialisation des données
        initData();

        System.out.println("\n=== Choisissez la stratégie de résolution ===");
        System.out.println("1 - Retry automatique");
        System.out.println("2 - Résolution manuelle en cas de conflit");
        System.out.print("Votre choix : ");
        int choix = scanner.nextInt();
        scanner.nextLine(); // consommer le retour chariot

        switch (choix) {
            case 1:
                System.out.println("\n=== Simulation avec retry automatique ===");
                simulateConcurrentReservationConflictWithRetry();
                break;

            case 2:
                System.out.println("\n=== Simulation avec résolution manuelle ===");
                simulateConcurrentReservationConflictWithManualChoice();
                break;

            default:
                System.out.println(" Choix invalide !");
        }

        emf.close();
    }



    private static void initData() {
        // Création d'un utilisateur
        Utilisateur utilisateur1 = new Utilisateur("Dupont", "Jean", "jean.dupont@example.com");
        Utilisateur utilisateur2 = new Utilisateur("Martin", "Sophie", "sophie.martin@example.com");

        // Création d'une salle
        Salle salle = new Salle("Salle A101", 30);
        salle.setDescription("Salle de réunion équipée d'un projecteur");

        // Persistance des entités
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("optimistic-locking-demo");
        try {
            var em = emf.createEntityManager();
            em.getTransaction().begin();

            em.persist(utilisateur1);
            em.persist(utilisateur2);
            em.persist(salle);

            // Création d'une réservation
            Reservation reservation = new Reservation(
                    LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                    LocalDateTime.now().plusDays(1).withHour(12).withMinute(0),
                    "Réunion d'équipe"
            );
            reservation.setUtilisateur(utilisateur1);
            reservation.setSalle(salle);

            em.persist(reservation);

            em.getTransaction().commit();
            em.close();

            System.out.println("Données initialisées avec succès !");
            System.out.println("Réservation créée : " + reservation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void simulateConcurrentReservationConflict() throws InterruptedException {
        // Récupération de la réservation
        Optional<Reservation> reservationOpt = reservationService.findById(1L);
        if (!reservationOpt.isPresent()) {
            System.out.println("Réservation non trouvée !");
            return;
        }

        Reservation reservation = reservationOpt.get();
        System.out.println("Réservation récupérée : " + reservation);

        // Création de deux threads qui vont modifier la même réservation
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            try {
                // Attendre que les deux threads soient prêts
                latch.await();

                // Premier thread : modification du motif
                Reservation r1 = reservationService.findById(1L).get();
                System.out.println("Thread 1 : Réservation récupérée, version = " + r1.getVersion());

                // Simuler un traitement long
                Thread.sleep(1000);

                r1.setMotif("Réunion d'équipe modifiée par Thread 1");
                try {
                    reservationService.update(r1);
                    System.out.println("Thread 1 : Réservation mise à jour avec succès !");
                } catch (OptimisticLockException e) {
                    System.out.println("Thread 1 : Conflit de verrouillage optimiste détecté !");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                // Attendre que les deux threads soient prêts
                latch.await();

                // Deuxième thread : modification des dates
                Reservation r2 = reservationService.findById(1L).get();
                System.out.println("Thread 2 : Réservation récupérée, version = " + r2.getVersion());

                // Modification immédiate
                r2.setDateDebut(r2.getDateDebut().plusHours(1));
                r2.setDateFin(r2.getDateFin().plusHours(1));

                try {
                    reservationService.update(r2);
                    System.out.println("Thread 2 : Réservation mise à jour avec succès !");
                } catch (OptimisticLockException e) {
                    System.out.println("Thread 2 : Conflit de verrouillage optimiste détecté !");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Démarrage des threads
        thread1.start();
        thread2.start();

        // Libération du latch pour que les deux threads commencent en même temps
        latch.countDown();

        // Attendre que les deux threads terminent
        thread1.join();
        thread2.join();

        // Vérification de l'état final de la réservation
        Optional<Reservation> finalReservationOpt = reservationService.findById(1L);
        finalReservationOpt.ifPresent(r -> {
            System.out.println("\nÉtat final de la réservation :");
            System.out.println("ID : " + r.getId());
            System.out.println("Motif : " + r.getMotif());
            System.out.println("Date début : " + r.getDateDebut());
            System.out.println("Date fin : " + r.getDateFin());
            System.out.println("Version : " + r.getVersion());
        });
    }

    private static void simulateConcurrentReservationConflictWithRetry() throws InterruptedException {
        // Création du handler avec 3 tentatives maximum
        OptimisticLockingRetryHandler retryHandler = new OptimisticLockingRetryHandler(reservationService, 3);

        // Création de deux threads qui vont modifier la même réservation
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            try {
                // Attendre que les deux threads soient prêts
                latch.await();

                // Premier thread : modification du motif avec retry
                retryHandler.executeWithRetry(1L, r -> {
                    System.out.println("Thread 1 : Modification du motif");
                    r.setMotif("Réunion d'équipe modifiée par Thread 1");

                    // Simuler un traitement long
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
                System.out.println("Thread 1 : Exception finale : " + e.getMessage());
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                // Attendre que les deux threads soient prêts
                latch.await();

                // Deuxième thread : modification des dates avec retry
                retryHandler.executeWithRetry(1L, r -> {
                    System.out.println("Thread 2 : Modification des dates");
                    r.setDateDebut(r.getDateDebut().plusHours(1));
                    r.setDateFin(r.getDateFin().plusHours(1));
                });
            } catch (Exception e) {
                System.out.println("Thread 2 : Exception finale : " + e.getMessage());
            }
        });

        // Démarrage des threads
        thread1.start();
        thread2.start();

        // Libération du latch pour que les deux threads commencent en même temps
        latch.countDown();

        // Attendre que les deux threads terminent
        thread1.join();
        thread2.join();

        // Vérification de l'état final de la réservation
        Optional<Reservation> finalReservationOpt = reservationService.findById(1L);
        finalReservationOpt.ifPresent(r -> {
            System.out.println("\nÉtat final de la réservation avec retry :");
            System.out.println("ID : " + r.getId());
            System.out.println("Motif : " + r.getMotif());
            System.out.println("Date début : " + r.getDateDebut());
            System.out.println("Date fin : " + r.getDateFin());
            System.out.println("Version : " + r.getVersion());
        });
    }

    private static void handleConflictManually(Reservation oldReservation, Reservation newReservation) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n Conflit détecté !");
        System.out.println("Ancienne réservation : " + oldReservation);
        System.out.println("Nouvelle tentative : " + newReservation);

        System.out.println("\nChoisissez l'action à effectuer :");
        System.out.println("1 - Garder l'ancienne réservation");
        System.out.println("2 - Appliquer les modifications récentes");

        int choice = scanner.nextInt();
        if (choice == 2) {
            oldReservation.setDateDebut(newReservation.getDateDebut());
            oldReservation.setDateFin(newReservation.getDateFin());
            oldReservation.setMotif(newReservation.getMotif());
            reservationService.update(oldReservation);
            System.out.println("Modifications appliquées !");
        } else {
            System.out.println(" Modifications annulées.");
        }
    }

    public static void simulateConcurrentReservationConflictWithManualChoice() {
        System.out.println("\n=== Simulation avec résolution manuelle ===");

        // Récupération de la réservation initiale
        Reservation reservationOriginale = reservationService.findById(1L)
                .orElseThrow(() -> new RuntimeException("Réservation introuvable"));

        // Thread 1 : modification de la réservation
        Thread thread1 = new Thread(() -> {
            try {
                Reservation r1 = reservationService.findById(reservationOriginale.getId())
                        .orElseThrow(() -> new RuntimeException("Réservation introuvable"));
                r1.setMotif("Réunion projet Thread 1");

                // Réassigner la salle persistée
                r1.setSalle(r1.getSalle());

                reservationService.update(r1);
                System.out.println("Thread 1 : Mise à jour réussie !");
            } catch (Exception e) {
                System.out.println("Thread 1 : Erreur -> " + e.getMessage());
            }
        });

        // Thread 2 : modification concurrente
        Thread thread2 = new Thread(() -> {
            try {
                Reservation r2 = reservationService.findById(reservationOriginale.getId())
                        .orElseThrow(() -> new RuntimeException("Réservation introuvable"));
                r2.setMotif("Réunion projet Thread 2");

                // Réassigner la salle persistée
                r2.setSalle(r2.getSalle());

                reservationService.update(r2);
                System.out.println("Thread 2 : Mise à jour réussie !");
            } catch (OptimisticLockException ole) {
                System.out.println("Thread 2 : Conflit détecté !");
                try {
                    Reservation latest = reservationService.findById(reservationOriginale.getId())
                            .orElseThrow(() -> new RuntimeException("Réservation introuvable"));
                    latest.setMotif("Réunion projet Thread 2 (après résolution manuelle)");
                    latest.setSalle(latest.getSalle());
                    reservationService.update(latest);
                    System.out.println("Thread 2 : Conflit résolu manuellement !");
                } catch (Exception ex) {
                    System.out.println("Thread 2 : Erreur lors de la résolution -> " + ex.getMessage());
                }
            } catch (Exception e) {
                System.out.println("Thread 2 : Erreur -> " + e.getMessage());
            }
        });

        // Lancer les threads
        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Afficher l’état final de la réservation
        Reservation finalReservation = reservationService.findById(reservationOriginale.getId())
                .orElseThrow(() -> new RuntimeException("Réservation introuvable"));
        System.out.println("\n=== État final de la réservation ===");
        System.out.println(finalReservation);
    }

    private static void simulatePessimisticLockingConflict() throws InterruptedException {
        System.out.println("\n=== Simulation avec Pessimistic Locking ===");

        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            var em = emf.createEntityManager();
            em.getTransaction().begin();
            Reservation r1 = em.find(Reservation.class, 1L, LockModeType.PESSIMISTIC_WRITE);
            System.out.println("Thread 1 a obtenu le verrou PESSIMISTIC_WRITE");
            try {
                Thread.sleep(2000); // Simulation de traitement long
                r1.setMotif("Pessimistic modification by Thread 1");
                em.getTransaction().commit();
            } catch (Exception e) {
                e.printStackTrace();
                em.getTransaction().rollback();
            } finally {
                em.close();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                var em = emf.createEntityManager();
                em.getTransaction().begin();
                System.out.println("Thread 2 tente d'accéder à la même réservation...");
                Reservation r2 = em.find(Reservation.class, 1L, LockModeType.PESSIMISTIC_WRITE);
                r2.setMotif("Pessimistic modification by Thread 2");
                em.getTransaction().commit();
                em.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        thread1.start();
        Thread.sleep(500); // Thread 1 démarre avant
        thread2.start();
        latch.countDown();

        thread1.join();
        thread2.join();
    }


}





