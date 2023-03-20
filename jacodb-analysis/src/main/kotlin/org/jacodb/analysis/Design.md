# Design

Primary languages: java, cpp.

Secondary: python, go, js, kotlin, etc.

## Logic
Main problem - there are no thoroughly labeled code benchmarks for static analyses. 
All benchmarks are based on real code and we don't know nor all execution path, nor precise result for point analysis,
nor knowledge of the amount of all available vulnerabilities.

So we want to create code, that of which we will definitely know all execution paths, complete set of vulnerabilities,
all sources and sinks, complete knowledge of all points to results and conditions on when they occur. 

Main idea - though it is impossible to create complete graph representation of arbitrary graph, we can 
generate some graph, which can be represented as a code. We will start from simple function calling, then add 
type hierarchy, data flow, lambdas and other stuff. Each will be represented as a part of pgraph
// все не что указано в тразите - идет в дефолт валуе. иначе берется из параметра.
// так как рендеринг в конце - все будет ок
// путь задаеися глобальным стейтом на доменах?))))
// НЕТ! так как у нас проблема может быть только на одном путе, только пройдя его полностью - то нам не нужно эмулировать
// диспатчинг в ифдс, он сам найдет только то, что нужно, а вот верификация будет за юсвм!!
// 1. как-то задавать анализы, для которых генерируем граф
// Это  должено реализовывать интерфейс какой-нибудь, который должен быть положен рядом-в класспас,
// мы его каким-нибудь рефлекшеном находим и радуемся
// 2. как-то задавать файл путь в который че генерим
// наверное хочу задавать путь до папки, в которую нужно класть проект. и да, туда сразу внутренности архива
// 3. как-то завязать кеш дфс
// просто реально держит ссылку на граф и просто мапчик да-нет и все
// 6. нужна презентация реальных функций и че она умеет
// функция - название, параметры, у параметра тип, и пишется он явно в формате джавы(другой язык мб потом)
// также функция имеет понимание, в каком порядке какие вызовы в ней будут делаться, и какие у каждого вызова параметры и в каком порядке
// изменения каждого параметра производятся перед самым вызовом в пути, тем самым гарантируем, что там не будут важны предыдущие значения
// также из этого следует, что мы не можем двумя разными способами вызываться в одном методе.
// !!!проблема - мы практически точно будет генерировать бесконечные рекурсии при любом цикле!!!
// то есть мы гарантированно должны быть ациклически! для этого будет использоваться стек, в который будет положен какое ребро нужно вызвать
// на данный момент мы поддерживаем явный путь в графе, но никак не "исполнение"(то есть как бы историю работы дфс).
// 4. как-то сделать реализацию vulnerabilities итдитп
// ну наверное ему нужен стартовая вершина, конечная, естественное весь путь, также функциональная репрезентация каждого,
// и каждый такой анализ дожен по этому путь пройтись и сам что-то сделать так, чтобы ничего не сломать остальным
// 5. сделать дампалку итогового состояния функций через жопу
// просто интерфейс, который принимает функцию из репрезентации и путь, куда это  надо написать. Дальше уже разбирается сама.
// их тоже можно искать сервисом

### Language features that are not supported
...and (most probably) will never be:
1. package-private/internal/sealed modifiers - as they are not present in all languages
2. cpp private inheritance, multiple inheritance - as this complicates code model way too much
3. cpp constructor initialization features - complicates ast too much