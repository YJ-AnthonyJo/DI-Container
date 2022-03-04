package org.di.framework;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.management.RuntimeErrorException;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.SearchConfig;
import org.di.framework.annotations.Component;
import org.di.framework.utils.InjectionUtil;

/**
 * Injector, to create objects for all @CustomService classes. autowire/inject
 * all dependencies
 */
public class Injector {
	private Map<Class<?>, Class<?>> diMap; //It's relation seems to be interface(or class) - implementation class
	// in case of class - implementation class, key and value are the same //짧게 말하고자 하는 목적(same as usual 등..)이 아니면 same앞에 the를 붙여말한다.
	private Map<Class<?>, Object> applicationScope; //It's relation seems to be class - object of the class

	/** diMap vs applicationScope : my assumption
	 * diMap : dependency injection map -> contains each relation of implementation class - interface(or class)
	 * applicationScope : contains each relation of all class(in application scope) - object
	 *
	 * -> When trying DI, injector finds implementation class of target's field-type(interface or class) in diMap.
	 * Then, injector finds created object of the implementation class and inject the object to field.
	 */

	private static Injector injector;

	private Injector() {
		super();
		diMap = new HashMap<>();
		applicationScope = new HashMap<>();
	}

	/**
	 * Start application
	 * 
	 * @param mainClass
	 */
	public static void startApplication(Class<?> mainClass) {
		try {
			synchronized (Injector.class) {
				if (injector == null) {
					injector = new Injector();
					injector.initFramework(mainClass);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static <T> T getService(Class<T> classz) {
		try {
			return injector.getBeanInstance(classz);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * initialize the injector framework
	 */
	private void initFramework(Class<?> mainClass)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
		Class<?>[] classes = getClasses(mainClass.getPackage().getName(), true); // get all classes include it's children classes based by first argument.

		//https://www.burningwave.org/how-to-find-all-classes-in-a-package/
		// -> Finding all classes of a package that have a particular annotation on at least one Field without considering the classes hierarchy
		// https://www.burningwave.org/how-to-scan-classes-for-specific-annotations-and-collect-their-values/
		// -> how to scan classes for specific annotations and collect their values
		//위와 가장 비슷한 듯.
		ComponentContainer componentConatiner = ComponentContainer.getInstance();
		ClassHunter classHunter = componentConatiner.getClassHunter();

		String packageRelPath = mainClass.getPackage().getName().replace(".", "/");
		try (ClassHunter.SearchResult result = classHunter.findBy(
				SearchConfig.forResources(
						packageRelPath
				).by(
						ClassCriteria.create().allThoseThatMatch(cls -> { //모든 조건을 만족해야함을 의미. 즉, @Component가 있는 모든(?) 클래스를 구하라는 의미.
							return cls.getAnnotation(Component.class) != null; // 클래스에서 파라미터로 들어간 어노테이션을 구해 반환한다. 없으면 null을 반환한다.
						})
				)
		)) {
			Collection<Class<?>> types = result.getClasses();
			for (Class<?> implementationClass : types) {
				Class<?>[] interfaces = implementationClass.getInterfaces();
				if (interfaces.length == 0) { // interface가 존재x -> client라는 의미이다. //maybe not.. so confusing.
					diMap.put(implementationClass, implementationClass);
				} else { //interface가 존재 -> service라는 의미이다.
					for (Class<?> iface : interfaces) {
						diMap.put(implementationClass, iface); //diMap : Map<Class<?>, Class<?>>
						/**음.. spring에서는 인터페이스형으로 안하고 클래스 자체로 해도 되던데..
						 * 근데 인터페이스로만 접근한다는 원칙이 있었던 것 같기도 하고..
						 */
					}
				}
			}

			for (Class<?> classz : classes) {
				if (classz.isAnnotationPresent(Component.class)) { //Component가 존재하면
					Object classInstance = classz.newInstance(); //해당 class의 instance 생성
					applicationScope.put(classz, classInstance); //applicationScope : Map<Class<?>, Object>
					InjectionUtil.autowire(this, classz, classInstance); //autowire method of InjectionUtil is static method
				}
			}
		};	

	}
	
	/**
	 * Get all the classes for the input package
	 */
	public Class<?>[] getClasses(String packageName, boolean recursive) throws ClassNotFoundException, IOException {
		ComponentContainer componentConatiner = ComponentContainer.getInstance();
		ClassHunter classHunter = componentConatiner.getClassHunter();
		String packageRelPath = packageName.replace(".", "/");
		SearchConfig config = SearchConfig.forResources(
			packageRelPath
		);
		if (!recursive) {
			config.findInChildren();
		}
		
		try (ClassHunter.SearchResult result = classHunter.findBy(config)) {
			Collection<Class<?>> classes = result.getClasses();
			return classes.toArray(new Class[classes.size()]);
		}	
	}


	/**
	 * Create and Get the Object instance of the implementation class for input
	 * interface service
	 */
	@SuppressWarnings("unchecked")
	private <T> T getBeanInstance(Class<T> interfaceClass) throws InstantiationException, IllegalAccessException {
		return (T) getBeanInstance(interfaceClass, null, null);
	}

	/**
	 * Overload getBeanInstance to handle qualifier and autowire by type
	 */
	public <T> Object getBeanInstance(Class<T> interfaceClass, String fieldName, String qualifier)
			throws InstantiationException, IllegalAccessException {
		//getImplimentClass : get the name of implementation class for arg1(interface) service
		Class<?> implementationClass = getImplimentationClass(interfaceClass, fieldName, qualifier);

		//if applicationScope contains object of `implementationClass` : return that object
		if (applicationScope.containsKey(implementationClass)) { //applicationScope : Map<Class<?>, Object>
			return applicationScope.get(implementationClass); //get object which class is `implementationClass`
		}

		//if applicationScope doesn't have the object of `implementationClass` : create one and return that object
		synchronized (applicationScope) { //synchronized : allow only this thread to access this data(it seems to be an applicationScope here) and prohibit other threads
			Object service = implementationClass.newInstance(); // create new instance
			applicationScope.put(implementationClass, service); // put into applicationScope Map
			return service; //return the created instance
		}
	}

	/**
	 * Get the name of the implimentation class for input interface service
	 */
	private Class<?> getImplimentationClass(Class<?> interfaceClass, final String fieldName, final String qualifier) {
		Set<Entry<Class<?>, Class<?>>> implementationClasses = diMap.entrySet().stream()
				.filter(entry -> entry.getValue() == interfaceClass).collect(Collectors.toSet());
		String errorMessage = "";
		if (implementationClasses == null || implementationClasses.size() == 0) {
			errorMessage = "no implementation found for interface " + interfaceClass.getName();
		} else if (implementationClasses.size() == 1) {
			Optional<Entry<Class<?>, Class<?>>> optional = implementationClasses.stream().findFirst();
			if (optional.isPresent()) {
				return optional.get().getKey();
			}
		} else if (implementationClasses.size() > 1) {
			final String findBy = (qualifier == null || qualifier.trim().length() == 0) ? fieldName : qualifier;

			//find by field name?? idk..
			//The developer of this DI Container seems to assume that the user names the field as implementation class name
			Optional<Entry<Class<?>, Class<?>>> optional = implementationClasses.stream()
					.filter(entry -> entry.getKey().getSimpleName().equalsIgnoreCase(findBy)).findAny();
			//getSimpleName() : get name of class which doesn't contain package name.
			//equalsIgnoreCase : check equality ignoring case(A, a -> a, A : ignore case)

			if (optional.isPresent()) {
				return optional.get().getKey();
			} else {
				errorMessage = "There are " + implementationClasses.size() + " of interface " + interfaceClass.getName()
						+ " Expected single implementation or make use of @CustomQualifier to resolve conflict";
			}
		}
		throw new RuntimeErrorException(new Error(errorMessage));
	}
}