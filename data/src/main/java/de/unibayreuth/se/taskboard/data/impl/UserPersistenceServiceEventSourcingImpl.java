package de.unibayreuth.se.taskboard.data.impl;

import de.unibayreuth.se.taskboard.business.domain.User;
import de.unibayreuth.se.taskboard.business.exceptions.DuplicateNameException;
import de.unibayreuth.se.taskboard.business.exceptions.UserNotFoundException;
import de.unibayreuth.se.taskboard.business.ports.UserPersistenceService;
import de.unibayreuth.se.taskboard.data.mapper.UserEntityMapper;
import de.unibayreuth.se.taskboard.data.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Primary
public class UserPersistenceServiceEventSourcingImpl implements UserPersistenceService {
    private final UserRepository userRepository;
    private final UserEntityMapper userEntityMapper;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void clear() {
        userRepository.findAll()
                .forEach(userEntity -> eventRepository.saveAndFlush(
                        EventEntity.deleteEventOf(userEntityMapper.fromEntity(userEntity), null))
                );
        if (userRepository.count() != 0) {
            throw new IllegalStateException("Tasks not successfully deleted.");
        }
    }

    @NonNull
    @Override
    public List<User> getAll() {
        return userRepository.findAll().stream()
                .map(userEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public Optional<User> getById(UUID id) {
        return userRepository.findById(id)
                .map(userEntityMapper::fromEntity);
    }

    @NonNull
    @Override
    public User upsert(User user) throws UserNotFoundException, DuplicateNameException {
        // TODO: Implement upsert
        /*
        The upsert method in the UserPersistenceServiceEventSourcingImpl class handles both the creation and updating of users.
        If the user ID is null, it creates a new user by generating a new UUID, saving an insert event, and returning the newly created user.
        If the user ID is not null, it updates the existing user by finding it in the repository, updating its fields, saving an update event, and returning the updated user.
        In both cases, it uses the EventRepository to log the changes and the UserRepository to persist the user data.
        */

        // Handle the case where the user ID is null (creating a new user)
        if (user.getId() == null) {

            // Create new user (no changes to check)
            user.setId(UUID.randomUUID());

            // Save the insert event first, before persisting the entity
            eventRepository.saveAndFlush(
                    EventEntity.insertEventOf(user, user.getId(), objectMapper)
            );

            // Save the user entity to the repository
            UserEntity savedEntity = userRepository.saveAndFlush(userEntityMapper.toEntity(user));

            // Return the user object
            return userEntityMapper.fromEntity(savedEntity);

        } else {
            // Handle the case where the user ID is not null (updating an existing user)
            UserEntity existingUserEntity = userRepository.findById(user.getId())
                    .orElseThrow(() -> new UserNotFoundException("User with ID " + user.getId() + " does not exist."));

            // Check for duplicate name, excluding the current user
            if (!existingUserEntity.getName().equals(user.getName()) && userRepository.existsByName(user.getName())) {
                throw new DuplicateNameException("User with name " + user.getName() + " already exists.");
            }

            // Update the fields of the existing user entity
            existingUserEntity.setName(user.getName());

            // Save the update event first, before persisting the entity
            eventRepository.saveAndFlush(
                    EventEntity.updateEventOf(user, existingUserEntity.getId(), objectMapper)
            );

            // Save the updated user entity
            UserEntity updatedEntity = userRepository.saveAndFlush(existingUserEntity);

            // Return the updated user object
            return userEntityMapper.fromEntity(updatedEntity);
        }
    }
}
