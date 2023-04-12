require: slotfilling/slotFilling.sc
    module = sys.zb-common
    
require: phoneNumber/phoneNumber.sc
    module = sys.zb-common
    
require: patterns.sc

require: discount.yaml
    var = discountInfo

require: city/cities-ru.csv
    module = sys.zb-common
    name = Cities
    var = $Cities

require: functions.js

init:
    bind("postProcess", function($context) {
        $context.session.lastState = $context.currentState;
    });

theme: /
    
    # state: AnyIntent
    #     event!: match
    #     a: Здравствуйте!
    
    # state: EntitySlot
    #     intent!: /поиск
    #     a: Вас интересует кто-то по имени {{ $parseTree._SlotName }}
    
    state: Direction
        intent!: /Ticket
        script:
            $session.destinationName = $nlp.inflect($parseTree._Destination.name, "accs");
            $session.departureName = $nlp.inflect($parseTree._Departure.name, "gent");
        a: Итак, вы летите в {{$session.destinationName}} из {{$session.departureName}} {{ $parseTree._Time.day }}.{{ $parseTree._Time.month }}
        
    # state: Entity
    #     q!: * меня зовут *
    #     script:
    #         log("сущность " + toPrettyString($entities))
    #     a: Вы назвали имя {{ $entities[0].value }}
        
    # state: EntityPattern
    #     q!: * где @Name::name *
    #     script:
    #         log("parseTree " + toPrettyString($parseTree))
    #     a: Здравствуйте, {{ $parseTree._name}}
    
    # state: Hello
    #     intent!: /привет/Вежливый
    #     intent!: /привет/Веселый
    #     a: {{ $context.intent.answer }}
    
    state: Proposal1
        q!: * предлож* 1 *
        script:
            $analytics.setMessageLabel("cust1", "Dom_ru_cust");
            $analytics.setSessionResult("Proposal1");
        a: 1
            
            
        state: Props1
            q: 1
            script:
                $analytics.setSessionLabel("Custom1");
                $analytics.setSessionResult("Props1");
            a: 1 1
                

    state: Proposal2
        q!: * предлож* 2 *
        script:
            $analytics.setMessageLabel("cust2", "Dom_ru_cust");
            $analytics.setSessionResult("Proposal2");
        a: 2
            
        state: Props2
            script:
                $analytics.setSessionLabel("Custom2");
                $analytics.setSessionResult("Props2");
            a: 2 2 

    state: Start
        q!: $regex</start>
        q: $regex</start> || fromState = "/Phone/Ask"
        q!: *start
        #q!: $hi
        script:
            $jsapi.startSession();
            $analytics.setSessionResult("Start");
        random:
            a: Здравствуйте!
            a: Приветствую!
        script:
            $response.replies = $response.replies || [];
            $response.replies.push({
                type: "image",
                imageUrl: "https://moslenta.ru/thumb/1200x0/filters:quality(75):no_upscale()/imgs/2023/02/28/08/5813098/59ca5afd0f7b39555ed31cbe39baa23adfd07067.jpg",
                text: "Самолет"
            });
        a: Меня зовут {{capitalize($injector.botName)}}.
        go!: /Service/SuggestHelp

    state: CatchAll || noContext = true
        event!: noMatch
        a: Простите, я не поняла. Переформулируйте, пожалуйста, свой запрос.
        go!: {{$session.lastState}}
        
theme: /Service
    
    state: SuggestHelp
        q: отмена || fromState = "/Phone/Ask"
        a: Давайте я помогу вам купить билет на самолет, хорошо?
        buttons:
            "Да" -> ./Accepted
            "Нет"
        
        state: Accepted
            q: * (да/давай*/хорошо) *
            a: Отлично!
            if: $client.phone
                go!: /Phone/Confirm
            else:
                go!: /Phone/Ask

        state: Rejected
            q: * (нет/не) *
            a: Боюсь, что ничего другого я пока предложить не могу...
            
theme: /Phone
    
    state: Ask || modal = true
        a: Для продолжения напишите, пожалуйста, ваш номер телефона.
        buttons:
            "Отмена"
    
        state: Get
            q: * $mobilePhoneNumber *
            script:
                $temp.phone = $parseTree._mobilePhoneNumber;
            go!: /Phone/Confirm
            
        state: LocalCatchAll
            event: noMatch
            a: Что-то это не похоже на номер телефона...
            go!: ..
            
    state: Confirm
        script:
            $temp.phone = $temp.phone || $client.phone;
        a: Ваш номер: {{$temp.phone}}, верно?
        script: $session.probablyPhone = $temp.phone;
        buttons:
            "Да"
            "Нет"
            
        state: Yes
            q: (да/верно)
            script:
                $client.phone = $session.probablyPhone;
                delete $session.probablyPhone;
                $reactions.transition("/Discount/Inform");
            
        state: No
            q: (нет/не [верно]/неверно)
            go!: /Phone/Ask
            
theme: /Discount
    
    state: Inform
        script:
            var nowDayOfWeek = $jsapi.dateForZone("Europe/Moscow", "EEEE");
            log(toPrettyString(nowDayOfWeek));
            var nowDate = $jsapi.dateForZone("Europe/Moscow", "dd.MM");
            
            var answerText = "Хочу отметить, что вам крупно повезло! Сегодня (" + nowDate + ") действует акция!";
            var discount = discountInfo[nowDayOfWeek];
            
            $reactions.answer(answerText);
            $reactions.answer(discount);
        go!: /City/Departure
        
            
theme: /City
    
    state: Departure
        a: Назовите, пожалуйста, город отправления.
        
        state: Get
            q: * $City *
            script:
                log(toPrettyString($parseTree));
                $session.departureCity = $parseTree._City;
                log("$session.departureCity: " + toPrettyString($session.departureCity));
            a: Итак, город отправления: {{$session.departureCity.name}}
            go!: ../../Arrival
                
        state: CatchAll || noContext = true
            event: noMatch
            a: Простите, я вас не поняла, назовите, пожалуйста, город отправления.

    state: Arrival
        a: Назовите, пожалуйста, город прибытия.
        
        state: Get
            q: * $City *
            script:
                log(toPrettyString($parseTree));
                $session.arrivalCity = $parseTree._City;
                log("$session.arrivalCity: " + toPrettyString($session.arrivalCity));
            a: Итак, город прибытия: {{$session.arrivalCity.name}}
            go!: /Weather/Current
                
        state: CatchAll || noContext = true
            event: noMatch
            a: Простите, я вас не поняла, назовите, пожалуйста, город прибытия.

theme: /Weather
    
    state: Current
        script:
            $temp.weather = getCurrentWeather($session.arrivalCity.lat, $session.arrivalCity.lon);
            log("$temp.weather "  + toPrettyString($temp.weather));
        if: ($temp.weather)
            a: В городе {{$session.arrivalCity.name}} сейчас {{$temp.weather.description}}, {{$temp.weather.temp}} C

    state: reset
        q!: reset
        script:
            $client = {};