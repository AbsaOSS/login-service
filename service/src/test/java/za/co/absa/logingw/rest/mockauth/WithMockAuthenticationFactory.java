/*
 * Copyright 2023 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.logingw.rest.mockauth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import za.co.absa.logingw.model.User;

import java.util.ArrayList;
import java.util.Arrays;

public class WithMockAuthenticationFactory implements WithSecurityContextFactory<WithMockAuthentication> {
    @Override
    public SecurityContext createSecurityContext(WithMockAuthentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Seq<String> scalaSeqGroups = JavaConverters.asScalaBuffer(Arrays.asList(authentication.userGroups())).toList();
        User principal = new User(authentication.userName(), authentication.userEmail(), scalaSeqGroups);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(principal, "mockPassword", new ArrayList<>());
        context.setAuthentication(auth);
        return context;
    }
}
