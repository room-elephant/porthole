package com.roomelephant.porthole.config;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.Container;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({Container.class, InspectContainerResponse.class, InspectImageResponse.class})
class DockerNativeConfig {}
