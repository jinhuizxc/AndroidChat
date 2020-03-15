package cn.wildfirechat.message.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by heavyrain lee on 2017/12/6.
 */

/**
 * 用来表示注解作用范围，超过这个作用范围，编译的时候就会报错
 *
 * ElementType的用法
 *
 * 取值	注解使用范围
 * TYPE	可用于类或者接口上
 * FIELD	可用于域上
 * METHOD	可用于方法上
 * PARAMETER	可用于参数上
 * CONSTRUCTOR	可用于构造方法上
 * LOCAL_VARIABLE	可用于局部变量上
 * ANNOTATION_TYPE	可用于注解类型上（被interface修饰的类型）
 * PACKAGE	用于记录java文件的package信息
 *
 * @Retention 定义了该Annotation被保留的时间长短
 * 取值	有效范围
 * SOURCE	在源文件中有效（即源文件保留）
 * CLASS	在class文件中有效（即class保留）
 * RUNTIMR	在运行时有效（即运行时保留）
 *
 * Documented注解表明这个注释是由 javadoc记录的，
 * 在默认情况下也有类似的记录工具。
 * 如果一个类型声明被注释了文档化，它的注释成为公共API的一部分
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)

public @interface ContentTag {
    int type() default 0;

    PersistFlag flag() default PersistFlag.No_Persist;
}
