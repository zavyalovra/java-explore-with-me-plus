package ru.practicum.ewm.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.dto.NewUserRequest;
import ru.practicum.ewm.user.dto.UserDto;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserDto create(NewUserRequest newUserRequest) {
        if (userRepository.existsByEmail(newUserRequest.getEmail())) {
            throw new ConflictException("User with email=" + newUserRequest.getEmail() + " already exists");
        }

        User user = userRepository.save(userMapper.toEntity(newUserRequest));
        return userMapper.toDto(user);
    }

    public List<UserDto> getAll(List<Long> ids, int from, int size) {
        validatePagination(from, size);

        Pageable pageable = PageRequest.of(from / size, size, DEFAULT_SORT);
        Page<User> page = (ids == null || ids.isEmpty())
                ? userRepository.findAll(pageable)
                : userRepository.findAllByIdIn(ids, pageable);

        return page.getContent().stream()
                .map(userMapper::toDto)
                .toList();
    }

    @Transactional
    public void delete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        userRepository.delete(user);
    }

    private void validatePagination(int from, int size) {
        if (from < 0) {
            throw new BadRequestException("Параметр 'from' должен быть больше или равен 0");
        }
        if (size <= 0) {
            throw new BadRequestException("Параметр 'size' должен быть больше 0");
        }
    }
}
