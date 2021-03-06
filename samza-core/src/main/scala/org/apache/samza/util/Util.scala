/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.samza.util

import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Random
import grizzled.slf4j.Logging
import org.apache.samza.SamzaException
import org.apache.samza.config.Config
import org.apache.samza.config.SystemConfig.Config2System
import org.apache.samza.config.TaskConfig.Config2Task
import scala.collection.JavaConversions._
import java.util.concurrent.ThreadFactory
import org.apache.samza.system.SystemFactory
import org.apache.samza.system.SystemStream

object Util extends Logging {
  val random = new Random

  /**
   * Make an environment variable string safe to pass.
   */
  def envVarEscape(str: String) = str.replace("\"", "\\\"").replace("'", "\\'")

  /**
   * Get a random number >= startInclusive, and < endExclusive.
   */
  def randomBetween(startInclusive: Int, endExclusive: Int) =
    startInclusive + random.nextInt(endExclusive - startInclusive)

  /**
   * Recursively remove a directory (or file), and all sub-directories. Equivalent
   * to rm -rf.
   */
  def rm(file: File) {
    if (file == null) {
      return
    } else if (file.isDirectory) {
      val files = file.listFiles()
      if (files != null) {
        for (f <- files)
          rm(f)
      }
      file.delete()
    } else {
      file.delete()
    }
  }

  /**
   * Instantiate a class instance from a given className.
   */
  def getObj[T](className: String) = {
    Class
      .forName(className)
      .newInstance
      .asInstanceOf[T]
  }

  /**
   * Uses config to create SystemAdmin classes for all input stream systems to
   * get each input stream's partition count, then returns the maximum count.
   * An input stream with two partitions, and a second input stream with four
   * partitions would result in this method returning 4.
   */
  def getMaxInputStreamPartitions(config: Config) = {
    val inputStreams = config.getInputStreams
    val systemNames = config.getSystemNames
    val systemAdmins = systemNames.map(systemName => {
      val systemFactoryClassName = config
        .getSystemFactory(systemName)
        .getOrElse(throw new SamzaException("A stream uses system %s, which is missing from the configuration." format systemName))
      val systemFactory = Util.getObj[SystemFactory](systemFactoryClassName)
      val systemAdmin = systemFactory.getAdmin(systemName, config)
      (systemName, systemAdmin)
    }).toMap
    inputStreams.flatMap(systemStream => {
      systemAdmins.get(systemStream.getSystem) match {
        case Some(sysAdmin) => sysAdmin.getPartitions(systemStream.getStream)
        case None => throw new IllegalArgumentException("Could not find a stream admin for system '" + systemStream.getSystem + "'")
      }
    }).toSet
  }

  /**
   * Returns a SystemStream object based on the system stream name given. For 
   * example, kafka.topic would return new SystemStream("kafka", "topic").
   */
  def getSystemStreamFromNames(systemStreamNames: String): SystemStream = {
    val idx = systemStreamNames.indexOf('.')
    if(idx < 0)
      throw new IllegalArgumentException("No '.' in stream name '" + systemStreamNames + "'. Stream names should be in the form 'system.stream'")
    new SystemStream(systemStreamNames.substring(0, idx), systemStreamNames.substring(idx + 1, systemStreamNames.length))
  }
  
  /**
   * Returns a SystemStream object based on the system stream name given. For 
   * example, kafka.topic would return new SystemStream("kafka", "topic").
   */
  def getNameFromSystemStream(systemStream: SystemStream) = {
    systemStream.getSystem + "." + systemStream.getStream
  }
}
