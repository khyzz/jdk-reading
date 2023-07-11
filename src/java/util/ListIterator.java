/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util;

/**
 List迭代器
 */
public interface ListIterator<E> extends Iterator<E> {
    // Query Operations

    /**
     * 判断下一个元素是否存在
     */
    boolean hasNext();

    /**
     * 返回下一个元素
     */
    E next();

    /**
     * 判断前一个元素是否存在
     */
    boolean hasPrevious();

    /**
     * 返回前一个元素
     */
    E previous();

    /**
     * 下一个访问位置
     */
    int nextIndex();

    /**
     * 上一个访问位置
     */
    int previousIndex();


    // Modification Operations

    /**
     * 移除元素
     */
    void remove();

    /**
     * 设置元素
     */
    void set(E e);

    /**
     * 添加元素
     */
    void add(E e);
}
