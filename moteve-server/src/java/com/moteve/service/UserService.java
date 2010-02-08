/*
 * Copyright 2009-2010 Moteve.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.moteve.service;

import com.moteve.dao.AuthorityDao;
import com.moteve.dao.GroupDao;
import com.moteve.dao.UserDao;
import com.moteve.domain.Authority;
import com.moteve.domain.Group;
import com.moteve.domain.User;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.NoResultException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Radek Skokan
 */
@Service
public class UserService {

    private static final Logger logger = Logger.getLogger(UserService.class);

    @Autowired
    private UserDao userDao;

    @Autowired
    private AuthorityDao authorityDao;

    @Autowired
    GroupDao groupDao;

    /**
     * Registers a new user into the system and grants him the MEMBER role.
     * 
     * @param user
     */
    public void register(User user) {
        logger.info("Registering user " + user.getEmail());
        user.setRegistrationDate(new Date());
        user.setEnabled(true);
        Authority memberAuthority = authorityDao.findByName(Authority.MEMBER);
        Set<Authority> authorities = new HashSet<Authority>();
        authorities.add(memberAuthority);
        user.setAuthorities(authorities);
        userDao.store(user);
    }

    /**
     * Authenticates the user by his email and password
     * @param email
     * @param password clear text password
     * @return the User object if the authentication is OK; otherwise null
     */
    public User authenticate(String email, String password) {
        try {
            return userDao.findByEmailAndPassword(email, password);
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Searches in existing users by the specified criteria by
     * <ul>
     * <li>email</li>
     * <li>display name</li>
     * </ul>
     *
     * @param criteria
     * @return matching Users
     */
    public List<User> findUsers(String criteria) {
        try {
            return userDao.findEnabledByEmailOrDisplayName(criteria);
        } catch (NoResultException e) {
            return new ArrayList<User>();
        }
    }

    /**
     * Adds contacts to a user.
     * @param email the user's email address
     * @param contactIds IDs of users to be added as contacts
     */
    public void addContacts(String email, List<Long> contactIds) {
        if (email == null || contactIds == null || contactIds.size() == 0) {
            return;
        }

        try {
            User user = userDao.findByEmail(email);
            // TODO: here we just want to add the already known contact IDs to that user. No need to SELECT all the contacts' details first.
            for (Long contactId : contactIds) {
                User contact = userDao.findById(contactId);
                user.getContacts().add(contact);
                logger.debug("Added contact " + contact.getEmail() + " to user " + user.getEmail());
            }
            userDao.store(user);
        } catch (NoResultException e) {
            logger.info("Cannot find user " + email + " in DB");
        }
    }

    public User findUserByEmail(String email) {
        try {
            return userDao.findByEmail(email);
        } catch (NoResultException e) {
            logger.info("Cannot find user " + email + " in DB");
            return null;
        }
    }

    /**
     *
     * @param email
     * @return set of groups that the user idenfied by email has
     */
    public Set<Group> getGroups(String email) {
        if (email == null) {
            return new HashSet<Group>();
        }

        try {
            return userDao.findByEmail(email).getGroups();
        } catch (NoResultException e) {
            logger.info("Cannot find user " + email + " in DB");
            return new HashSet<Group>();
        }
    }

    /**
     * Creates a group groupName for the user identified by email.
     * @param email
     * @param groupName
     */
    public void createGroup(String email, String groupName) {
        if (email == null) {
            return;
        }

        try {
            User user = userDao.findByEmail(email);
            Group group = new Group();
            group.setName(groupName);
            group.setUser(user);
            groupDao.store(group);
        } catch (NoResultException e) {
            logger.info("Cannot find user " + email + " in DB");
        }
    }

    /**
     * Removes groups associated to a user.
     * @param email identifies the user
     * @param groupsToRemove IDs of the groups to be removed
     */
    public void removeGroups(String email, List<Long> groupsToRemove) {
        if (email == null || groupsToRemove == null || groupsToRemove.size() == 0) {
            return;
        }

        for (Long groupId : groupsToRemove) {
            groupDao.delete(groupId);
        }
    }
}
