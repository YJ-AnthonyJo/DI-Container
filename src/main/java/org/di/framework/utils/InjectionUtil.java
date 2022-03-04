package org.di.framework.utils;

import static org.burningwave.core.assembler.StaticComponentContainer.Fields;

import java.lang.reflect.Field;
import java.util.Collection;

import org.burningwave.core.classes.FieldCriteria;
import org.di.framework.Injector;
import org.di.framework.annotations.Autowired;
import org.di.framework.annotations.Qualifier;

public class InjectionUtil {

	private InjectionUtil() {
		super();
	}

	/**
	 * Perform injection recursively, for each service inside the Client class
	 */
	public static void autowire(Injector injector, Class<?> classz, Object classInstance)
			throws InstantiationException, IllegalAccessException {

		//Get all filtered field(Autowire annotation presented) values of an object(in here, class??) through memory address access
		Collection<Field> fields = Fields.findAllAndMakeThemAccessible(
			FieldCriteria.forEntireClassHierarchy().allThoseThatMatch(field ->
				field.isAnnotationPresent(Autowired.class)
			), 
			classz
		);
		for (Field field : fields) {
			String qualifier = field.isAnnotationPresent(Qualifier.class)
					? field.getAnnotation(Qualifier.class).value()
					: null;
			Object fieldInstance = injector.getBeanInstance(field.getType(), field.getName(), qualifier);
			//arg1 : interfaceClass -> find implementation class of this interface
			//arg2 : field name -> if number of implementation is bigger than 1 and qualifier(arg3) is null
			// 			: Assume field name as implementation class name and find implementation class by the name
			//arg3 : qualifier

			Fields.setDirect(classInstance, field, fieldInstance);// there is any references.. haha..
			//Assume..
			/**
			 * setDirect invoke setFieldValue method.
			 * 	setDirect definition : setDirect(Object target, Field field, Object value)
			 * 		invoke code of setFieldValue : setFieldValue(target, field, value);
			 * setFieldValue method seems to set field value as given object(argument 3, `value`)
			 * so.. It seems that `Field.setDirect(classInstance, field, fieldInstane)` performs Dependency Injection
			 */
			autowire(injector, fieldInstance.getClass(), fieldInstance);
			// repeat this recursively if `fieldInstance`(value of the field) has fields and these fields need Dependency Injection too..
		}
	}

}
